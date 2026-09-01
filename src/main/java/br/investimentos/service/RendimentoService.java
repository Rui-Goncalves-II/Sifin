package br.investimentos.service;

import br.investimentos.model.Movimentacao;
import br.investimentos.model.VtaMensal;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.repository.MovimentacaoRepository;
import br.investimentos.repository.VaiAnualRepository;
import br.investimentos.repository.VtaMensalRepository;

import java.util.List;
import java.util.Optional;


public class RendimentoService {

    private final VaiAnualRepository vaiRepo;
    private final MovimentacaoRepository movRepo;
    private final VtaMensalRepository vtaRepo;

    public RendimentoService(VaiAnualRepository vaiRepo, MovimentacaoRepository movRepo, VtaMensalRepository vtaRepo) {
        this.vaiRepo = vaiRepo;
        this.movRepo = movRepo;
        this.vtaRepo = vtaRepo;
    }

    /** SAM = Σ(DEPOSITO − SAQUE) do inicio do ano até a data informada (inclusive). */
    public double calcularSam(int investimentoId, int ano, int ateOMes, int ateODia) {
        List<Movimentacao> movs = movRepo.findByInvestimentoAnoMesDiaAte(investimentoId, ano, ateOMes, ateODia);
        double sam = 0;
        for (Movimentacao m : movs) {
            sam += m.getTipoMov() == TipoMovimentacao.DEPOSITO ? m.getValor() : -m.getValor();
        }
        return sam;
    }

    /** VI = VAI + SAM, calculado até uma data (dia/mês) específica dentro do ano. */
    public double calcularVi(int investimentoId, int ano, int mes, int dia) {
        double vai = vaiRepo.getVai(investimentoId, ano);
        double sam = calcularSam(investimentoId, ano, mes, dia);
        return vai + sam;
    }

    /** R = VTA − VI para uma entrada de VTA. Pode ser negativo. */
    public double calcularR(int investimentoId, int mes, int ano) {
        Optional<VtaMensal> opt = vtaRepo.findUltimoDoMes(investimentoId, mes, ano);
        return opt.map(this::calcularR).orElse(0.0);
    }

    /** R a partir de um VtaMensal já carregado (evita consulta dupla). */
    public double calcularR(VtaMensal vta) {
        double vi = calcularVi(vta.getInvestimentoId(), vta.getPeriodoAno(), vta.getPeriodoMes(), vta.getPeriodoDia());
        return vta.getVta() - vi;
    }

    /** Total histórico de depósitos líquidos (DEPOSITO − SAQUE) de um ativo RF. */
    public double calcularTotalAportado(int investimentoId) {
        double total = 0;
        for (Movimentacao m : movRepo.findByInvestimento(investimentoId))
            total += m.getTipoMov() == TipoMovimentacao.DEPOSITO ? m.getValor() : -m.getValor();
        return total;
    }

    /** Soma de R de todos os anos informados (último VTA de cada ano). */
    public double calcularRTotal(int investimentoId, List<Integer> anos) {
        double total = 0;
        for (int ano : anos)
            total += vtaRepo.findUltimoDoAno(investimentoId, ano)
                    .map(this::calcularR).orElse(0.0);
        return total;
    }
}
