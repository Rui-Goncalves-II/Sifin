package br.investimentos.service;

import br.investimentos.model.*;
import br.investimentos.model.enums.*;
import br.investimentos.repository.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ImportExportService {

    public record ImportResult(int ativosNovos, int movimentacoes, int vtas, int vais, int aportes, int vacs) {
        public String toMessage() {
            return String.format(
                "Importação concluída!\n\nAtivos novos criados: %d\nMovimentações adicionadas: %d\n" +
                "VTAs importadas: %d\nVAIs importados: %d\nAportes RV adicionados: %d\nVACs importadas: %d",
                ativosNovos, movimentacoes, vtas, vais, aportes, vacs);
        }
    }

    private static final String HEADER =
        "record_type,nome_ativo,tipo_ativo,subtipo,indexador,taxa_anual,data_vencimento,moeda," +
        "ativo,notas_ativo,periodo_mes,periodo_ano,tipo_mov,valor,cotacao_dolar,notas," +
        "tipo_op,quantidade,preco_por_cota,vta,vac,fonte_vac,ano_vai,vai";

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final VtaMensalRepository vtaRepo;
    private final VaiAnualRepository vaiRepo;
    private final AporteRvRepository aporteRepo;
    private final VacMensalRepository vacRepo;

    public ImportExportService(InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
            VtaMensalRepository vtaRepo, VaiAnualRepository vaiRepo,
            AporteRvRepository aporteRepo, VacMensalRepository vacRepo) {
        this.invRepo = invRepo; this.movRepo = movRepo;
        this.vtaRepo = vtaRepo; this.vaiRepo = vaiRepo;
        this.aporteRepo = aporteRepo; this.vacRepo = vacRepo;
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
        }
    }

    public ImportResult importar(File arquivo) throws IOException {
        List<String[]> rows = lerCsv(arquivo);

        Map<String, Integer> nomeParaId = new HashMap<>();
        for (Investimento inv : invRepo.findAllInclArquivados())
            nomeParaId.put(inv.getNome(), inv.getId());

        int ativosNovos = 0, movs = 0, vtas = 0, vais = 0, aportes = 0, vacs = 0;

        for (String[] row : rows) {
            if (row.length < 24 || !"INVESTIMENTO".equals(row[0])) continue;
            String nome = row[1];
            if (!nomeParaId.containsKey(nome)) {
                Investimento inv = parseInvestimento(row);
                invRepo.salvar(inv);
                nomeParaId.put(nome, inv.getId());
                ativosNovos++;
            }
        }

        for (String[] row : rows) {
            if (row.length < 24) continue;
            String tipo = row[0];
            Integer invId = nomeParaId.get(row[1]);
            if (invId == null) continue;
            switch (tipo) {
                case "MOVIMENTACAO" -> { movRepo.salvar(parseMovimentacao(row, invId)); movs++; }
                case "VTA_MENSAL"   -> { vtaRepo.salvar(parseVta(row, invId)); vtas++; }
                case "VAI_ANUAL"    -> { vaiRepo.salvar(parseVai(row, invId)); vais++; }
                case "APORTE_RV"    -> { aporteRepo.salvar(parseAporte(row, invId)); aportes++; }
                case "VAC_MENSAL"   -> { vacRepo.salvar(parseVac(row, invId)); vacs++; }
            }
        }

        return new ImportResult(ativosNovos, movs, vtas, vais, aportes, vacs);
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
        return csvLine(f);
    }

    private String linhaVta(String nome, VtaMensal vta) {
        String[] f = empty("VTA_MENSAL", nome);
        f[10] = String.valueOf(vta.getPeriodoMes());
        f[11] = String.valueOf(vta.getPeriodoAno());
        f[19] = String.valueOf(vta.getVta());
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

    private Movimentacao parseMovimentacao(String[] row, int invId) {
        Movimentacao m = new Movimentacao();
        m.setInvestimentoId(invId);
        m.setPeriodoMes(Integer.parseInt(row[10]));
        m.setPeriodoAno(Integer.parseInt(row[11]));
        m.setTipoMov(TipoMovimentacao.valueOf(row[12]));
        m.setValor(Double.parseDouble(row[13]));
        m.setCotacaoDolar(dNull(row[14]));
        m.setNotas(nullIfEmpty(row[15]));
        return m;
    }

    private VtaMensal parseVta(String[] row, int invId) {
        VtaMensal v = new VtaMensal();
        v.setInvestimentoId(invId);
        v.setPeriodoMes(Integer.parseInt(row[10]));
        v.setPeriodoAno(Integer.parseInt(row[11]));
        v.setVta(Double.parseDouble(row[19]));
        return v;
    }

    private VaiAnual parseVai(String[] row, int invId) {
        VaiAnual v = new VaiAnual();
        v.setInvestimentoId(invId);
        v.setAno(Integer.parseInt(row[22]));
        v.setVai(Double.parseDouble(row[23]));
        return v;
    }

    private AporteRv parseAporte(String[] row, int invId) {
        AporteRv a = new AporteRv();
        a.setInvestimentoId(invId);
        a.setPeriodoMes(Integer.parseInt(row[10]));
        a.setPeriodoAno(Integer.parseInt(row[11]));
        a.setValor(Double.parseDouble(row[13]));
        a.setNotas(nullIfEmpty(row[15]));
        a.setTipoOp(TipoOperacaoRv.valueOf(row[16]));
        a.setQuantidade(dNull(row[17]));
        a.setPrecoPorCota(dNull(row[18]));
        return a;
    }

    private VacMensal parseVac(String[] row, int invId) {
        VacMensal v = new VacMensal();
        v.setInvestimentoId(invId);
        v.setPeriodoMes(Integer.parseInt(row[10]));
        v.setPeriodoAno(Integer.parseInt(row[11]));
        v.setVac(Double.parseDouble(row[20]));
        v.setFonte(row[21].isEmpty() ? "MANUAL" : row[21]);
        return v;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String[] empty(String type, String nome) {
        String[] f = new String[24];
        Arrays.fill(f, "");
        f[0] = type;
        f[1] = nome;
        return f;
    }

    private static String s(String v) { return v == null ? "" : v; }
    private static String d(Double v) { return v == null ? "" : String.valueOf(v); }

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

    private static List<String[]> lerCsv(File arquivo) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(arquivo), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.isBlank()) continue;
                rows.add(parseLine(line));
            }
        }
        return rows;
    }

    private static String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"'); i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(cur.toString());
                    cur = new StringBuilder();
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static Double dNull(String s) {
        return (s == null || s.isEmpty()) ? null : Double.parseDouble(s);
    }
}
