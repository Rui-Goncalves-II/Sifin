package br.investimentos.service;

import br.investimentos.model.Investimento;
import br.investimentos.model.VtaMensal;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.model.enums.TipoOperacaoRv;
import br.investimentos.repository.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import br.investimentos.model.AporteRv;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.VacMensal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class ConsolidacaoService {

    public static final int ANO_TODOS = -1;

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final VtaMensalRepository vtaRepo;
    private final AporteRvRepository aporteRepo;
    private final VacMensalRepository vacRepo;
    private final RendimentoService rendimentoService;
    private final RendaVariavelService rvService;

    public record ResultadoConsolidado(
            double viarf, double rarf, double vtarf,
            double viarv, double parv, double dta, double vtarv,
            double vtia, double vtra, double pta,
            Double pcpa, Double prat,
            boolean todosOsAnos
    ) {}

    public ConsolidacaoService(InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
                                VtaMensalRepository vtaRepo, AporteRvRepository aporteRepo,
                                VacMensalRepository vacRepo, RendimentoService rendimentoService,
                                RendaVariavelService rvService) {
        this.invRepo = invRepo;
        this.movRepo = movRepo;
        this.vtaRepo = vtaRepo;
        this.aporteRepo = aporteRepo;
        this.vacRepo = vacRepo;
        this.rendimentoService = rendimentoService;
        this.rvService = rvService;
    }

    public ResultadoConsolidado calcular(int ano, Set<TipoInvestimento> tipos) {
        Set<TipoInvestimento> tiposRf = new java.util.HashSet<>(tipos);
        tiposRf.retainAll(EnumSet.of(TipoInvestimento.RENDA_FIXA));
        Set<TipoInvestimento> tiposRv = new java.util.HashSet<>(tipos);
        tiposRv.retainAll(EnumSet.of(TipoInvestimento.RENDA_VARIAVEL));

        boolean todos = ano == ANO_TODOS;
        double viarf = 0, rarf = 0;

        if (!tiposRf.isEmpty()) {
            for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_FIXA)) {
                List<VtaMensal> vtas = todos ? vtaRepo.findByInvestimento(inv.getId())
                        : vtaRepo.findByInvestimentoEAno(inv.getId(), ano);
                if (todos) {
                    for (var mov : movRepo.findByInvestimento(inv.getId()))
                        viarf += mov.getTipoMov() == TipoMovimentacao.DEPOSITO ? mov.getValor() : -mov.getValor();
                } else {
                    for (var mov : movRepo.findByInvestimentoEAno(inv.getId(), ano))
                        viarf += mov.getTipoMov() == TipoMovimentacao.DEPOSITO ? mov.getValor() : -mov.getValor();
                }
                if (!vtas.isEmpty()) rarf += rendimentoService.calcularR(vtas.get(vtas.size() - 1));
            }
        }

        double vtarf = viarf + rarf;
        double viarv = 0, dta = 0, parv = 0;

        if (!tiposRv.isEmpty()) {
            for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_VARIAVEL)) {
                var aportes = todos ? aporteRepo.findByInvestimento(inv.getId())
                        : aporteRepo.findByInvestimentoEAno(inv.getId(), ano);
                for (var a : aportes) {
                    if (a.getTipoOp() == TipoOperacaoRv.COMPRA) viarv += a.getValor();
                    else if (a.getTipoOp() == TipoOperacaoRv.DIVIDENDO) dta += a.getValor();
                }
                var pos = rvService.calcular(inv.getId());
                parv += pos.vac() * pos.qc();
            }
        }

        double vtarv = parv + dta;
        double vtia = viarf + viarv;
        double vtra = rarf + dta;
        double pta = vtarf + vtarv;
        Double pcpa = pta > 0 ? (vtra / pta) * 100 : null;
        Double prat = vtia > 0 ? (vtra / vtia) * 100 : null;

        return new ResultadoConsolidado(viarf, rarf, vtarf, viarv, parv, dta, vtarv,
                vtia, vtra, pta, pcpa, prat, todos);
    }

    public ResultadoConsolidado calcular(int ano) {
        boolean todos = ano == ANO_TODOS;

        // VIARF: Σ depósitos líquidos RF
        double viarf = 0;
        // RARF: Σ rendimentos RF (último R de cada ativo no ano)
        double rarf = 0;

        List<Investimento> rfList = invRepo.findByTipo(TipoInvestimento.RENDA_FIXA);
        for (Investimento inv : rfList) {
            List<VtaMensal> vtas;
            if (todos) {
                vtas = vtaRepo.findByInvestimento(inv.getId());
            } else {
                vtas = vtaRepo.findByInvestimentoEAno(inv.getId(), ano);
            }

            // VIARF: soma movimentações (depósitos - saques)
            if (todos) {
                for (var mov : movRepo.findByInvestimento(inv.getId())) {
                    viarf += mov.getTipoMov() == TipoMovimentacao.DEPOSITO ? mov.getValor() : -mov.getValor();
                }
            } else {
                for (var mov : movRepo.findByInvestimentoEAno(inv.getId(), ano)) {
                    viarf += mov.getTipoMov() == TipoMovimentacao.DEPOSITO ? mov.getValor() : -mov.getValor();
                }
            }

            // RARF: último VTA do período
            if (!vtas.isEmpty()) {
                VtaMensal ultimo = vtas.get(vtas.size() - 1);
                rarf += rendimentoService.calcularR(ultimo);
            }
        }

        double vtarf = viarf + rarf;

        // VIARV: Σ COMPRAs RV no ano
        double viarv = 0;
        double dta = 0;
        double parv = 0;

        List<Investimento> rvList = invRepo.findByTipo(TipoInvestimento.RENDA_VARIAVEL);
        for (Investimento inv : rvList) {
            var aportes = todos ? aporteRepo.findByInvestimento(inv.getId())
                    : aporteRepo.findByInvestimentoEAno(inv.getId(), ano);
            for (var a : aportes) {
                if (a.getTipoOp() == TipoOperacaoRv.COMPRA) viarv += a.getValor();
                else if (a.getTipoOp() == TipoOperacaoRv.DIVIDENDO) dta += a.getValor();
            }
            // PARV: sempre valor atual
            var pos = rvService.calcular(inv.getId());
            parv += pos.vac() * pos.qc();
        }

        double vtarv = parv + dta;
        double vtia = viarf + viarv;
        double vtra = rarf + dta;
        double pta = vtarf + vtarv;

        Double pcpa = pta > 0 ? (vtra / pta) * 100 : null;
        Double prat = vtia > 0 ? (vtra / vtia) * 100 : null;

        return new ResultadoConsolidado(
                viarf, rarf, vtarf,
                viarv, parv, dta, vtarv,
                vtia, vtra, pta,
                pcpa, prat, todos
        );
    }

    public double calcularAportesDoMes(int mes, int ano) {
        return calcularAportesDoMes(mes, ano, EnumSet.allOf(TipoInvestimento.class));
    }

    public double calcularAportesDoMes(int mes, int ano, Set<TipoInvestimento> tipos) {
        double total = 0;
        for (Investimento inv : invRepo.findAll()) {
            if (!tipos.contains(inv.getTipo())) continue;
            if (inv.getTipo() == TipoInvestimento.RENDA_FIXA || inv.getTipo() == TipoInvestimento.DOLAR) {
                for (var mov : movRepo.findByInvestimentoAnoMes(inv.getId(), ano, mes))
                    if (mov.getTipoMov() == TipoMovimentacao.DEPOSITO) total += mov.getValor();
            } else if (inv.getTipo() == TipoInvestimento.RENDA_VARIAVEL) {
                for (var a : aporteRepo.findByInvestimentoAnoMes(inv.getId(), ano, mes))
                    if (a.getTipoOp() == TipoOperacaoRv.COMPRA) total += a.getValor();
            }
        }
        return total;
    }

    public record VtraMensal(int mes, int ano, double vtra) {}

    /**
     * Retorna VTRA real por mês, incluindo apenas meses com dados (RF com VTA ou RV com dividendo).
     * Chave de ordenação: ano*100+mes para ordenação natural.
     */
    public List<VtraMensal> calcularVtraPorMes(int ano) {
        return calcularVtraPorMes(ano, EnumSet.allOf(TipoInvestimento.class));
    }

    public List<VtraMensal> calcularVtraPorMes(int ano, Set<TipoInvestimento> tipos) {
        boolean todos = (ano == ANO_TODOS);
        java.util.TreeMap<Integer, Double> mapa = new java.util.TreeMap<>();

        if (tipos.contains(TipoInvestimento.RENDA_FIXA)) {
            for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_FIXA)) {
                List<VtaMensal> vtas = todos
                        ? vtaRepo.findByInvestimento(inv.getId())
                        : vtaRepo.findByInvestimentoEAno(inv.getId(), ano);
                // R é cumulativo no ano (VTA - (VAI+SAM)). Para obter o rendimento
                // incremental de cada mês, subtraímos o R do mês anterior no mesmo ano.
                VtaMensal prev = null;
                for (VtaMensal vta : vtas) {
                    double rCumul = rendimentoService.calcularR(vta);
                    double rIncremental;
                    if (prev == null || prev.getPeriodoAno() != vta.getPeriodoAno()) {
                        rIncremental = rCumul;
                    } else {
                        rIncremental = rCumul - rendimentoService.calcularR(prev);
                    }
                    prev = vta;
                    int chave = vta.getPeriodoAno() * 100 + vta.getPeriodoMes();
                    mapa.merge(chave, rIncremental, Double::sum);
                }
            }
        }

        if (tipos.contains(TipoInvestimento.RENDA_VARIAVEL)) {
            for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_VARIAVEL)) {
                var aportes = todos
                        ? aporteRepo.findByInvestimento(inv.getId())
                        : aporteRepo.findByInvestimentoEAno(inv.getId(), ano);
                for (var a : aportes) {
                    if (a.getTipoOp() == TipoOperacaoRv.DIVIDENDO) {
                        int chave = a.getPeriodoAno() * 100 + a.getPeriodoMes();
                        mapa.merge(chave, a.getValor(), Double::sum);
                    }
                }
            }
        }

        List<VtraMensal> resultado = new ArrayList<>();
        for (var e : mapa.entrySet()) {
            if (e.getValue() != 0.0) {
                resultado.add(new VtraMensal(e.getKey() % 100, e.getKey() / 100, e.getValue()));
            }
        }
        return resultado;
    }

    public record ExtratoMensal(int mes, int ano, double valorAplicado, double valorRendimento, double valorTotal) {}

    public record PatrimonioMensal(int mes, int ano, double pta) {}

    /**
     * PTA real por mês: para cada mês com dados, usa último VTA/VAC conhecido
     * até aquele mês para cada ativo, respeitando o filtro de tipos.
     * Dólar usa a cotação atual como aproximação de todos os meses.
     */
    public List<PatrimonioMensal> calcularPtaPorMes(int ano, Set<TipoInvestimento> tipos, double cotacaoDolar) {
        boolean todos = (ano == ANO_TODOS);

        Map<Integer, List<VtaMensal>> vtaMap = new HashMap<>();
        Map<Integer, List<VacMensal>> vacMap = new HashMap<>();
        Map<Integer, List<AporteRv>> aportesMap = new HashMap<>();
        Map<Integer, List<Movimentacao>> movMap = new HashMap<>();
        TreeSet<Integer> chavesMeses = new TreeSet<>();

        if (tipos.contains(TipoInvestimento.RENDA_FIXA)) {
            for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_FIXA)) {
                List<VtaMensal> vtas = vtaRepo.findByInvestimento(inv.getId());
                vtaMap.put(inv.getId(), vtas);
                vtas.stream()
                    .filter(v -> todos || v.getPeriodoAno() == ano)
                    .forEach(v -> chavesMeses.add(v.getPeriodoAno() * 100 + v.getPeriodoMes()));
            }
        }

        if (tipos.contains(TipoInvestimento.RENDA_VARIAVEL)) {
            for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_VARIAVEL)) {
                vacMap.put(inv.getId(), vacRepo.findByInvestimento(inv.getId()));
                aportesMap.put(inv.getId(), aporteRepo.findByInvestimento(inv.getId()));
                vacMap.get(inv.getId()).stream()
                    .filter(v -> todos || v.getPeriodoAno() == ano)
                    .forEach(v -> chavesMeses.add(v.getPeriodoAno() * 100 + v.getPeriodoMes()));
            }
        }

        if (tipos.contains(TipoInvestimento.DOLAR)) {
            for (Investimento inv : invRepo.findByTipo(TipoInvestimento.DOLAR)) {
                List<Movimentacao> movs = movRepo.findByInvestimento(inv.getId());
                movMap.put(inv.getId(), movs);
                movs.stream()
                    .filter(m -> todos || m.getPeriodoAno() == ano)
                    .forEach(m -> chavesMeses.add(m.getPeriodoAno() * 100 + m.getPeriodoMes()));
            }
        }

        if (chavesMeses.isEmpty()) return List.of();

        List<PatrimonioMensal> resultado = new ArrayList<>();

        for (int chave : chavesMeses) {
            double pta = 0;

            // RF: último VTA de cada ativo até este mês
            for (List<VtaMensal> vtas : vtaMap.values()) {
                pta += vtas.stream()
                    .filter(v -> v.getPeriodoAno() * 100 + v.getPeriodoMes() <= chave)
                    .mapToDouble(VtaMensal::getVta)
                    .reduce(0.0, (a, b) -> b);
            }

            // RV: último VAC × QC acumulado até este mês para cada ativo
            for (int invId : vacMap.keySet()) {
                double vac = vacMap.get(invId).stream()
                    .filter(v -> v.getPeriodoAno() * 100 + v.getPeriodoMes() <= chave)
                    .mapToDouble(VacMensal::getVac)
                    .reduce(0.0, (a, b) -> b);
                if (vac <= 0) continue;
                double qc = 0;
                for (AporteRv ap : aportesMap.getOrDefault(invId, List.of())) {
                    if (ap.getPeriodoAno() * 100 + ap.getPeriodoMes() > chave) continue;
                    if (ap.getTipoOp() == TipoOperacaoRv.COMPRA && ap.getQuantidade() != null)
                        qc += ap.getQuantidade();
                    else if (ap.getTipoOp() == TipoOperacaoRv.VENDA && ap.getQuantidade() != null)
                        qc -= ap.getQuantidade();
                }
                pta += Math.max(0, qc) * vac;
            }

            // Dólar: saldo USD acumulado até este mês × cotação atual
            for (List<Movimentacao> movs : movMap.values()) {
                double usd = 0;
                for (Movimentacao mov : movs) {
                    if (mov.getPeriodoAno() * 100 + mov.getPeriodoMes() > chave) continue;
                    usd += mov.getTipoMov() == TipoMovimentacao.DEPOSITO ? mov.getValor() : -mov.getValor();
                }
                pta += Math.max(0, usd) * cotacaoDolar;
            }

            resultado.add(new PatrimonioMensal(chave % 100, chave / 100, pta));
        }

        return resultado;
    }

    /**
     * Histórico mensal unificado: para cada mês com dados, retorna valor aplicado,
     * rendimento incremental e valor total do portfólio, respeitando o filtro de tipos.
     */
    public List<ExtratoMensal> calcularExtratoPorMes(int ano, Set<TipoInvestimento> tipos, double cotacaoDolar) {
        boolean todos = (ano == ANO_TODOS);

        Map<Integer, List<VtaMensal>>    vtaMap      = new HashMap<>();
        Map<Integer, List<VacMensal>>    vacMap      = new HashMap<>();
        Map<Integer, List<AporteRv>>     apMap       = new HashMap<>();
        Map<Integer, List<Movimentacao>> movRfMap    = new HashMap<>();
        Map<Integer, List<Movimentacao>> movDolarMap = new HashMap<>();
        TreeSet<Integer> chaves = new TreeSet<>();

        if (tipos.contains(TipoInvestimento.RENDA_FIXA)) {
            for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_FIXA)) {
                List<VtaMensal>    vtas = vtaRepo.findByInvestimento(inv.getId());
                List<Movimentacao> movs = movRepo.findByInvestimento(inv.getId());
                vtaMap.put(inv.getId(), vtas);
                movRfMap.put(inv.getId(), movs);
                vtas.stream().filter(v -> todos || v.getPeriodoAno() == ano)
                    .forEach(v -> chaves.add(v.getPeriodoAno() * 100 + v.getPeriodoMes()));
                movs.stream().filter(m -> todos || m.getPeriodoAno() == ano)
                    .forEach(m -> chaves.add(m.getPeriodoAno() * 100 + m.getPeriodoMes()));
            }
        }

        if (tipos.contains(TipoInvestimento.RENDA_VARIAVEL)) {
            for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_VARIAVEL)) {
                vacMap.put(inv.getId(), vacRepo.findByInvestimento(inv.getId()));
                apMap.put(inv.getId(), aporteRepo.findByInvestimento(inv.getId()));
                vacMap.get(inv.getId()).stream().filter(v -> todos || v.getPeriodoAno() == ano)
                    .forEach(v -> chaves.add(v.getPeriodoAno() * 100 + v.getPeriodoMes()));
                apMap.get(inv.getId()).stream().filter(a -> todos || a.getPeriodoAno() == ano)
                    .forEach(a -> chaves.add(a.getPeriodoAno() * 100 + a.getPeriodoMes()));
            }
        }

        if (tipos.contains(TipoInvestimento.DOLAR)) {
            for (Investimento inv : invRepo.findByTipo(TipoInvestimento.DOLAR)) {
                List<Movimentacao> movs = movRepo.findByInvestimento(inv.getId());
                movDolarMap.put(inv.getId(), movs);
                movs.stream().filter(m -> todos || m.getPeriodoAno() == ano)
                    .forEach(m -> chaves.add(m.getPeriodoAno() * 100 + m.getPeriodoMes()));
            }
        }

        if (chaves.isEmpty()) return List.of();

        // R de RF é cumulativo no ano; pré-computamos o incremento por ativo/mês
        Map<Integer, Map<Integer, Double>> rIncMap = new HashMap<>();
        for (Map.Entry<Integer, List<VtaMensal>> e : vtaMap.entrySet()) {
            Map<Integer, Double> rm = new HashMap<>();
            VtaMensal prev = null;
            for (VtaMensal vta : e.getValue()) {
                double rCumul = rendimentoService.calcularR(vta);
                double rInc = (prev == null || prev.getPeriodoAno() != vta.getPeriodoAno())
                        ? rCumul : rCumul - rendimentoService.calcularR(prev);
                prev = vta;
                rm.put(vta.getPeriodoAno() * 100 + vta.getPeriodoMes(), rInc);
            }
            rIncMap.put(e.getKey(), rm);
        }

        List<ExtratoMensal> resultado = new ArrayList<>();

        for (int chave : chaves) {
            int m = chave % 100;
            int a = chave / 100;
            double aplicado = 0, rendimento = 0, total = 0;

            // ── Renda Fixa ──────────────────────────────────────────────────
            for (Map.Entry<Integer, List<VtaMensal>> e : vtaMap.entrySet()) {
                int id = e.getKey();
                for (Movimentacao mov : movRfMap.getOrDefault(id, List.of()))
                    if (mov.getPeriodoAno() * 100 + mov.getPeriodoMes() == chave)
                        aplicado += mov.getTipoMov() == TipoMovimentacao.DEPOSITO
                                ? mov.getValor() : -mov.getValor();
                rendimento += rIncMap.getOrDefault(id, Map.of()).getOrDefault(chave, 0.0);
                total += e.getValue().stream()
                        .filter(v -> v.getPeriodoAno() * 100 + v.getPeriodoMes() <= chave)
                        .mapToDouble(VtaMensal::getVta).reduce(0.0, (x, y) -> y);
            }

            // ── Renda Variável ──────────────────────────────────────────────
            for (int id : vacMap.keySet()) {
                for (AporteRv ap : apMap.getOrDefault(id, List.of())) {
                    int apChave = ap.getPeriodoAno() * 100 + ap.getPeriodoMes();
                    if (apChave != chave) continue;
                    if (ap.getTipoOp() == TipoOperacaoRv.COMPRA)         aplicado   += ap.getValor();
                    else if (ap.getTipoOp() == TipoOperacaoRv.DIVIDENDO) rendimento += ap.getValor();
                }
                double vac = vacMap.get(id).stream()
                        .filter(v -> v.getPeriodoAno() * 100 + v.getPeriodoMes() <= chave)
                        .mapToDouble(VacMensal::getVac).reduce(0.0, (x, y) -> y);
                if (vac > 0) {
                    double qc = 0;
                    for (AporteRv ap : apMap.getOrDefault(id, List.of())) {
                        if (ap.getPeriodoAno() * 100 + ap.getPeriodoMes() > chave) continue;
                        if (ap.getTipoOp() == TipoOperacaoRv.COMPRA && ap.getQuantidade() != null)
                            qc += ap.getQuantidade();
                        else if (ap.getTipoOp() == TipoOperacaoRv.VENDA && ap.getQuantidade() != null)
                            qc -= ap.getQuantidade();
                    }
                    total += Math.max(0, qc) * vac;
                }
            }

            // ── Dólar ───────────────────────────────────────────────────────
            for (List<Movimentacao> movs : movDolarMap.values()) {
                double usd = 0;
                for (Movimentacao mov : movs) {
                    int mc = mov.getPeriodoAno() * 100 + mov.getPeriodoMes();
                    double delta = mov.getTipoMov() == TipoMovimentacao.DEPOSITO ? mov.getValor() : -mov.getValor();
                    if (mc == chave) aplicado += delta * cotacaoDolar;
                    if (mc <= chave) usd += delta;
                }
                total += Math.max(0, usd) * cotacaoDolar;
            }

            resultado.add(new ExtratoMensal(m, a, aplicado, rendimento, total));
        }

        resultado.sort((x, y) -> (x.ano() * 100 + x.mes()) - (y.ano() * 100 + y.mes()));
        return resultado;
    }

    public List<Integer> anosComDados() {
        List<Integer> anos = new ArrayList<>();
        try (Connection c = br.investimentos.db.DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT DISTINCT periodo_ano FROM vta_mensal " +
                    "UNION SELECT DISTINCT periodo_ano FROM aportes_rv " +
                    "UNION SELECT DISTINCT periodo_ano FROM movimentacoes " +
                    "ORDER BY 1";
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) anos.add(rs.getInt(1));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return anos;
    }
}
