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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        double total = 0;
        for (Investimento inv : invRepo.findAll()) {
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
        boolean todos = (ano == ANO_TODOS);
        java.util.TreeMap<Integer, Double> mapa = new java.util.TreeMap<>();

        for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_FIXA)) {
            List<VtaMensal> vtas = todos
                    ? vtaRepo.findByInvestimento(inv.getId())
                    : vtaRepo.findByInvestimentoEAno(inv.getId(), ano);
            for (VtaMensal vta : vtas) {
                int chave = vta.getPeriodoAno() * 100 + vta.getPeriodoMes();
                mapa.merge(chave, rendimentoService.calcularR(vta), Double::sum);
            }
        }

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

        List<VtraMensal> resultado = new ArrayList<>();
        for (var e : mapa.entrySet()) {
            if (e.getValue() != 0.0) {
                resultado.add(new VtraMensal(e.getKey() % 100, e.getKey() / 100, e.getValue()));
            }
        }
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
