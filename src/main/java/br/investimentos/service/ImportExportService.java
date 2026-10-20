package br.investimentos.service;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.*;
import br.investimentos.model.enums.*;
import br.investimentos.repository.*;
import br.investimentos.model.enums.TipoGasto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class ImportExportService {

    public record ImportResult(int ativosNovos, int movimentacoes, int vtas, int vais, int aportes, int vacs,
                               int gastos, int configs, int cotacoes, int ignoradas, File backup) {
        public String toMessage() {
            String msg = String.format(
                "Importação concluída!\n\nAtivos novos criados: %d\nMovimentações adicionadas: %d\n" +
                "VTAs importadas: %d\nVAIs importados: %d\nAportes RV adicionados: %d\nVACs importadas: %d\n" +
                "Gastos importados: %d\nConfigurações restauradas: %d\nCotações do dólar: %d",
                ativosNovos, movimentacoes, vtas, vais, aportes, vacs, gastos, configs, cotacoes);
            if (ignoradas > 0)
                msg += "\n\n⚠ Linhas ignoradas (tipo desconhecido ou ativo inexistente): " + ignoradas;
            if (backup != null)
                msg += "\n\nBackup do banco anterior salvo em:\n" + backup.getAbsolutePath();
            return msg;
        }
    }

    /** Conteúdo de um CSV já validado, pronto para ser gravado no banco. */
    public static final class DadosImportacao {
        private final List<Investimento> investimentos = new ArrayList<>();
        private final List<Map.Entry<String, Object>> filhos = new ArrayList<>(); // nome do ativo -> registro
        private final List<Gasto> gastos = new ArrayList<>();
        private final Map<String, String> configs = new LinkedHashMap<>();
        private final List<CotacaoDolar> cotacoes = new ArrayList<>();
        private int ignoradas;
    }

    // Colunas 0..30 são o formato original; periodo_dia (31) foi adicionada ao final para manter
    // compatibilidade com CSVs antigos. CONFIG usa nome_ativo=chave e notas=valor; COTACAO_DOLAR usa
    // nome_ativo=data, valor=compra, cotacao_dolar=venda e notas=fonte.
    private static final String HEADER =
        "record_type,nome_ativo,tipo_ativo,subtipo,indexador,taxa_anual,data_vencimento,moeda," +
        "ativo,notas_ativo,periodo_mes,periodo_ano,tipo_mov,valor,cotacao_dolar,notas," +
        "tipo_op,quantidade,preco_por_cota,vta,vac,fonte_vac,ano_vai,vai," +
        "gasto_tipo,gasto_descricao,gasto_fim_mes,gasto_fim_ano,gasto_parcelas_total,gasto_parcela_numero,gasto_grupo_parcela," +
        "periodo_dia";
    private static final int NUM_COLUNAS = 32;
    private static final int COL_DIA = 31;

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final VtaMensalRepository vtaRepo;
    private final VaiAnualRepository vaiRepo;
    private final AporteRvRepository aporteRepo;
    private final VacMensalRepository vacRepo;
    private final GastoRepository gastoRepo;
    private final CotacaoRepository cotacaoRepo;
    private final ConfigService configSvc;

    public ImportExportService(InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
            VtaMensalRepository vtaRepo, VaiAnualRepository vaiRepo,
            AporteRvRepository aporteRepo, VacMensalRepository vacRepo,
            GastoRepository gastoRepo, CotacaoRepository cotacaoRepo, ConfigService configSvc) {
        this.invRepo = invRepo; this.movRepo = movRepo;
        this.vtaRepo = vtaRepo; this.vaiRepo = vaiRepo;
        this.aporteRepo = aporteRepo; this.vacRepo = vacRepo;
        this.gastoRepo = gastoRepo;
        this.cotacaoRepo = cotacaoRepo; this.configSvc = configSvc;
    }

    public void exportar(File arquivo) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(arquivo), StandardCharsets.UTF_8)))) {
            pw.println(HEADER);
            for (Investimento inv : invRepo.findAllInclArquivados()) {
                pw.println(linhaInvestimento(inv));
                for (Movimentacao m : movRepo.findByInvestimento(inv.getId()))
                    pw.println(linhaMovimentacao(inv.getNome(), m));
                for (VtaMensal vta : vtaRepo.findByInvestimento(inv.getId()))
                    pw.println(linhaVta(inv.getNome(), vta));
                for (VaiAnual vai : vaiRepo.findByInvestimento(inv.getId()))
                    pw.println(linhaVai(inv.getNome(), vai));
                for (AporteRv a : aporteRepo.findByInvestimento(inv.getId()))
                    pw.println(linhaAporte(inv.getNome(), a));
                for (VacMensal vac : vacRepo.findByInvestimento(inv.getId()))
                    pw.println(linhaVac(inv.getNome(), vac));
            }
            for (Gasto g : gastoRepo.findAll())
                pw.println(linhaGasto(g));
            for (Map.Entry<String, String> e : configSvc.findAll().entrySet())
                pw.println(linhaConfig(e.getKey(), e.getValue()));
            for (CotacaoDolar c : cotacaoRepo.findAll())
                pw.println(linhaCotacao(c));
        }
    }

    /** Adiciona o conteúdo do CSV ao banco atual. */
    public ImportResult importar(File arquivo) throws IOException {
        return gravar(ler(arquivo), null);
    }

    /**
     * Lê e valida o CSV inteiro sem tocar no banco. Qualquer linha inválida lança exceção
     * indicando o número da linha, para que uma restauração nunca apague dados à toa.
     */
    public DadosImportacao ler(File arquivo) throws IOException {
        DadosImportacao dados = new DadosImportacao();
        List<String[]> rows = lerCsv(arquivo);
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            try {
                parseRegistro(row, dados);
            } catch (RuntimeException e) {
                throw new IOException("Registro " + (i + 1) + " (" + row[0] + ") inválido: " + e.getMessage(), e);
            }
        }
        return dados;
    }

    /**
     * Substitui todo o banco pelo conteúdo já validado. Antes de apagar, salva uma cópia do banco atual.
     * Se o CSV não tiver configurações (formato antigo), as configurações atuais são mantidas.
     */
    public ImportResult restaurar(DadosImportacao dados) {
        DatabaseManager db = DatabaseManager.getInstance();
        File backup = db.criarBackup();
        Map<String, String> configsAtuais = configSvc.findAll();
        db.limparBancoDados();
        if (dados.configs.isEmpty())
            configsAtuais.forEach(configSvc::set);
        return gravar(dados, backup);
    }

    private void parseRegistro(String[] row, DadosImportacao dados) {
        String tipo = row[0];
        if (row.length < 24) { dados.ignoradas++; return; }
        switch (tipo) {
            case "INVESTIMENTO"  -> dados.investimentos.add(parseInvestimento(row));
            case "MOVIMENTACAO"  -> dados.filhos.add(Map.entry(row[1], parseMovimentacao(row)));
            case "VTA_MENSAL"    -> dados.filhos.add(Map.entry(row[1], parseVta(row)));
            case "VAI_ANUAL"     -> dados.filhos.add(Map.entry(row[1], parseVai(row)));
            case "APORTE_RV"     -> dados.filhos.add(Map.entry(row[1], parseAporte(row)));
            case "VAC_MENSAL"    -> dados.filhos.add(Map.entry(row[1], parseVac(row)));
            case "GASTO"         -> {
                if (row.length >= 31) dados.gastos.add(parseGasto(row));
                else dados.ignoradas++;
            }
            case "CONFIG"        -> dados.configs.put(row[1], row[15]);
            case "COTACAO_DOLAR" -> dados.cotacoes.add(parseCotacao(row));
            default              -> dados.ignoradas++;
        }
    }

    private ImportResult gravar(DadosImportacao dados, File backup) {
        Map<String, Integer> nomeParaId = new HashMap<>();
        for (Investimento inv : invRepo.findAllInclArquivados())
            nomeParaId.put(inv.getNome(), inv.getId());

        int ativosNovos = 0, movs = 0, vtas = 0, vais = 0, aportes = 0, vacs = 0, ignoradas = dados.ignoradas;

        for (Investimento inv : dados.investimentos) {
            if (nomeParaId.containsKey(inv.getNome())) continue;
            invRepo.salvar(inv);
            nomeParaId.put(inv.getNome(), inv.getId());
            ativosNovos++;
        }

        for (Map.Entry<String, Object> filho : dados.filhos) {
            Integer invId = nomeParaId.get(filho.getKey());
            if (invId == null) { ignoradas++; continue; }
            Object o = filho.getValue();
            if (o instanceof Movimentacao m)   { m.setInvestimentoId(invId); movRepo.salvar(m); movs++; }
            else if (o instanceof VtaMensal v) { v.setInvestimentoId(invId); vtaRepo.salvar(v); vtas++; }
            else if (o instanceof VaiAnual v)  { v.setInvestimentoId(invId); vaiRepo.salvar(v); vais++; }
            else if (o instanceof AporteRv a)  { a.setInvestimentoId(invId); aporteRepo.salvar(a); aportes++; }
            else if (o instanceof VacMensal v) { v.setInvestimentoId(invId); vacRepo.salvar(v); vacs++; }
        }

        for (Gasto g : dados.gastos) gastoRepo.salvar(g);
        dados.configs.forEach(configSvc::set);
        for (CotacaoDolar c : dados.cotacoes) cotacaoRepo.salvar(c);

        return new ImportResult(ativosNovos, movs, vtas, vais, aportes, vacs, dados.gastos.size(),
                dados.configs.size(), dados.cotacoes.size(), ignoradas, backup);
    }

    // ── CSV writing ──────────────────────────────────────────────────────────

    private String linhaInvestimento(Investimento inv) {
        String[] f = empty("INVESTIMENTO", inv.getNome());
        f[2] = inv.getTipo().name();
        f[3] = s(inv.getSubtipo());
        f[4] = s(inv.getIndexador());
        f[5] = d(inv.getTaxaAnual());
        f[6] = s(inv.getDataVencimento());
        f[7] = s(inv.getMoeda());
        f[8] = inv.isAtivo() ? "1" : "0";
        f[9] = s(inv.getNotas());
        return csvLine(f);
    }

    private String linhaMovimentacao(String nome, Movimentacao m) {
        String[] f = empty("MOVIMENTACAO", nome);
        f[10] = String.valueOf(m.getPeriodoMes());
        f[11] = String.valueOf(m.getPeriodoAno());
        f[12] = m.getTipoMov().name();
        f[13] = String.valueOf(m.getValor());
        f[14] = d(m.getCotacaoDolar());
        f[15] = s(m.getNotas());
        f[COL_DIA] = String.valueOf(m.getPeriodoDia());
        return csvLine(f);
    }

    private String linhaVta(String nome, VtaMensal vta) {
        String[] f = empty("VTA_MENSAL", nome);
        f[10] = String.valueOf(vta.getPeriodoMes());
        f[11] = String.valueOf(vta.getPeriodoAno());
        f[19] = String.valueOf(vta.getVta());
        f[COL_DIA] = String.valueOf(vta.getPeriodoDia());
        return csvLine(f);
    }

    private String linhaVai(String nome, VaiAnual vai) {
        String[] f = empty("VAI_ANUAL", nome);
        f[22] = String.valueOf(vai.getAno());
        f[23] = String.valueOf(vai.getVai());
        return csvLine(f);
    }

    private String linhaAporte(String nome, AporteRv a) {
        String[] f = empty("APORTE_RV", nome);
        f[10] = String.valueOf(a.getPeriodoMes());
        f[11] = String.valueOf(a.getPeriodoAno());
        f[13] = String.valueOf(a.getValor());
        f[15] = s(a.getNotas());
        f[16] = a.getTipoOp().name();
        f[17] = d(a.getQuantidade());
        f[18] = d(a.getPrecoPorCota());
        f[COL_DIA] = String.valueOf(a.getPeriodoDia());
        return csvLine(f);
    }

    private String linhaVac(String nome, VacMensal vac) {
        String[] f = empty("VAC_MENSAL", nome);
        f[10] = String.valueOf(vac.getPeriodoMes());
        f[11] = String.valueOf(vac.getPeriodoAno());
        f[20] = String.valueOf(vac.getVac());
        f[21] = s(vac.getFonte());
        return csvLine(f);
    }

    private String linhaGasto(Gasto g) {
        String[] f = empty("GASTO", "");
        f[10] = g.getPeriodoMes() != null ? String.valueOf(g.getPeriodoMes()) : "";
        f[11] = g.getPeriodoAno() != null ? String.valueOf(g.getPeriodoAno()) : "";
        f[13] = String.valueOf(g.getValor());
        f[15] = s(g.getNotas());
        f[24] = g.getTipo().name();
        f[25] = s(g.getDescricao());
        f[26] = g.getFimMes() != null ? String.valueOf(g.getFimMes()) : "";
        f[27] = g.getFimAno() != null ? String.valueOf(g.getFimAno()) : "";
        f[28] = String.valueOf(g.getParcelasTotal());
        f[29] = String.valueOf(g.getParcelaNumero());
        f[30] = s(g.getGrupoParcela());
        f[COL_DIA] = String.valueOf(g.getPeriodoDia());
        return csvLine(f);
    }

    private String linhaConfig(String chave, String valor) {
        String[] f = empty("CONFIG", chave);
        f[15] = s(valor);
        return csvLine(f);
    }

    private String linhaCotacao(CotacaoDolar c) {
        String[] f = empty("COTACAO_DOLAR", c.getData());
        f[13] = String.valueOf(c.getValorCompra());
        f[14] = String.valueOf(c.getValorVenda());
        f[15] = s(c.getFonte());
        return csvLine(f);
    }

    // ── CSV parsing ──────────────────────────────────────────────────────────

    private Investimento parseInvestimento(String[] row) {
        Investimento inv = new Investimento();
        inv.setNome(row[1]);
        inv.setTipo(TipoInvestimento.valueOf(row[2]));
        inv.setSubtipo(nullIfEmpty(row[3]));
        inv.setIndexador(nullIfEmpty(row[4]));
        inv.setTaxaAnual(dNull(row[5]));
        inv.setDataVencimento(nullIfEmpty(row[6]));
        inv.setMoeda(row[7].isEmpty() ? "BRL" : row[7]);
        inv.setAtivo(!"0".equals(row[8]));
        inv.setNotas(nullIfEmpty(row[9]));
        return inv;
    }

    private Movimentacao parseMovimentacao(String[] row) {
        Movimentacao m = new Movimentacao();
        m.setPeriodoDia(dia(row));
        m.setPeriodoMes(Integer.parseInt(row[10]));
        m.setPeriodoAno(Integer.parseInt(row[11]));
        m.setTipoMov(TipoMovimentacao.valueOf(row[12]));
        m.setValor(Double.parseDouble(row[13]));
        m.setCotacaoDolar(dNull(row[14]));
        m.setNotas(nullIfEmpty(row[15]));
        return m;
    }

    private VtaMensal parseVta(String[] row) {
        VtaMensal v = new VtaMensal();
        v.setPeriodoDia(dia(row));
        v.setPeriodoMes(Integer.parseInt(row[10]));
        v.setPeriodoAno(Integer.parseInt(row[11]));
        v.setVta(Double.parseDouble(row[19]));
        return v;
    }

    private VaiAnual parseVai(String[] row) {
        VaiAnual v = new VaiAnual();
        v.setAno(Integer.parseInt(row[22]));
        v.setVai(Double.parseDouble(row[23]));
        return v;
    }

    private AporteRv parseAporte(String[] row) {
        AporteRv a = new AporteRv();
        a.setPeriodoDia(dia(row));
        a.setPeriodoMes(Integer.parseInt(row[10]));
        a.setPeriodoAno(Integer.parseInt(row[11]));
        a.setValor(Double.parseDouble(row[13]));
        a.setNotas(nullIfEmpty(row[15]));
        a.setTipoOp(TipoOperacaoRv.valueOf(row[16]));
        a.setQuantidade(dNull(row[17]));
        a.setPrecoPorCota(dNull(row[18]));
        return a;
    }

    private VacMensal parseVac(String[] row) {
        VacMensal v = new VacMensal();
        v.setPeriodoMes(Integer.parseInt(row[10]));
        v.setPeriodoAno(Integer.parseInt(row[11]));
        v.setVac(Double.parseDouble(row[20]));
        v.setFonte(row[21].isEmpty() ? "MANUAL" : row[21]);
        return v;
    }

    private Gasto parseGasto(String[] row) {
        Gasto g = new Gasto();
        g.setTipo(TipoGasto.valueOf(row[24]));
        g.setDescricao(row[25]);
        g.setPeriodoDia(dia(row));
        g.setPeriodoMes(row[10].isEmpty() ? null : Integer.parseInt(row[10]));
        g.setPeriodoAno(row[11].isEmpty() ? null : Integer.parseInt(row[11]));
        g.setValor(Double.parseDouble(row[13]));
        g.setNotas(nullIfEmpty(row[15]));
        g.setFimMes(row[26].isEmpty() ? null : Integer.parseInt(row[26]));
        g.setFimAno(row[27].isEmpty() ? null : Integer.parseInt(row[27]));
        g.setParcelasTotal(row[28].isEmpty() ? 1 : Integer.parseInt(row[28]));
        g.setParcelaNumero(row[29].isEmpty() ? 1 : Integer.parseInt(row[29]));
        g.setGrupoParcela(nullIfEmpty(row[30]));
        return g;
    }

    private CotacaoDolar parseCotacao(String[] row) {
        CotacaoDolar c = new CotacaoDolar();
        c.setData(row[1]);
        c.setValorCompra(Double.parseDouble(row[13]));
        c.setValorVenda(Double.parseDouble(row[14]));
        c.setFonte(nullIfEmpty(row[15]));
        return c;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String[] empty(String type, String nome) {
        String[] f = new String[NUM_COLUNAS];
        Arrays.fill(f, "");
        f[0] = type;
        f[1] = nome;
        return f;
    }

    private static String s(String v) { return v == null ? "" : v; }
    private static String d(Double v) { return v == null ? "" : String.valueOf(v); }

    /** CSVs exportados antes da coluna periodo_dia assumem dia 1. */
    private static int dia(String[] row) {
        return row.length > COL_DIA && !row[COL_DIA].isEmpty() ? Integer.parseInt(row[COL_DIA]) : 1;
    }

    private static String csvLine(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            String f = fields[i] == null ? "" : fields[i];
            if (f.contains(",") || f.contains("\"") || f.contains("\r") || f.contains("\n")) {
                sb.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(f);
            }
        }
        return sb.toString();
    }

    /** Lê o arquivo inteiro respeitando quebras de linha dentro de campos entre aspas. Ignora o cabeçalho. */
    private static List<String[]> lerCsv(File arquivo) throws IOException {
        String conteudo = Files.readString(arquivo.toPath(), StandardCharsets.UTF_8);
        if (conteudo.startsWith("﻿")) conteudo = conteudo.substring(1);

        List<String[]> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        boolean header = true;
        int n = conteudo.length();
        for (int i = 0; i <= n; i++) {
            char c = i < n ? conteudo.charAt(i) : '\n';
            if (inQuotes && i < n) {
                if (c == '"') {
                    if (i + 1 < n && conteudo.charAt(i + 1) == '"') {
                        cur.append('"'); i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur = new StringBuilder();
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < n && conteudo.charAt(i + 1) == '\n') i++;
                fields.add(cur.toString());
                cur = new StringBuilder();
                boolean blank = fields.size() == 1 && fields.get(0).isBlank();
                if (header) header = false;
                else if (!blank) rows.add(fields.toArray(new String[0]));
                fields = new ArrayList<>();
            } else {
                cur.append(c);
            }
        }
        return rows;
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static Double dNull(String s) {
        return (s == null || s.isEmpty()) ? null : Double.parseDouble(s);
    }
}
