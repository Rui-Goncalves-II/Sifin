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

    /** SAM = Σ(DEPOSITO − SAQUE) do inicio do ano até o mês informado (inclusive). */
    public double calcularSam(int investimentoId, int ano, int ateOMes) {
        List<Movimentacao> movs = movRepo.findByInvestimentoAnoMesAte(investimentoId, ano, ateOMes);
        double sam = 0;
        for (Movimentacao m : movs) {
            sam += m.getTipoMov() == TipoMovimentacao.DEPOSITO ? m.getValor() : -m.getValor();
        }
        return sam;
    }

    /** VI = VAI + SAM */
    public double calcularVi(int investimentoId, int ano, int mes) {
        double vai = vaiRepo.getVai(investimentoId, ano);
        double sam = calcularSam(investimentoId, ano, mes);
        return vai + sam;
    }

    /** R = VTA − VI para uma entrada de VTA. Pode ser negativo. */
    public double calcularR(int investimentoId, int mes, int ano) {
        Optional<VtaMensal> opt = vtaRepo.find(investimentoId, mes, ano);
        if (opt.isEmpty()) return 0;
        double vta = opt.get().getVta();
        double vi = calcularVi(investimentoId, ano, mes);
        return vta - vi;
    }

    /** R a partir de um VtaMensal já carregado (evita consulta dupla). */
    public double calcularR(VtaMensal vta) {
        double vi = calcularVi(vta.getInvestimentoId(), vta.getPeriodoAno(), vta.getPeriodoMes());
        return vta.getVta() - vi;
    }

    /**
     * Soma dos rendimentos dos meses anteriores ao mês informado no mesmo ano.
     * Equivale ao R cumulativo do mês imediatamente anterior com VTA no mesmo ano.
     * Propriedade telescópica: RM_1 + RM_2 + ... + RM_{M-1} = R_{M-1}
     */
    public double calcularSomaRendimentosAnteriores(int investimentoId, int mes, int ano) {
        List<VtaMensal> vtasDoAno = vtaRepo.findByInvestimentoEAno(investimentoId, ano);
        VtaMensal prevVta = null;
        for (VtaMensal v : vtasDoAno) {
            if (v.getPeriodoMes() < mes) prevVta = v;
        }
        return prevVta == null ? 0.0 : calcularR(prevVta);
    }
}
