package br.investimentos.service;

import br.investimentos.model.AporteRv;
import br.investimentos.model.VacMensal;
import br.investimentos.model.enums.TipoOperacaoRv;
import br.investimentos.repository.AporteRvRepository;
import br.investimentos.repository.VacMensalRepository;

import java.util.List;
import java.util.Optional;

public class RendaVariavelService {

    private final AporteRvRepository aporteRepo;
    private final VacMensalRepository vacRepo;

    public RendaVariavelService(AporteRvRepository aporteRepo, VacMensalRepository vacRepo) {
        this.aporteRepo = aporteRepo;
        this.vacRepo = vacRepo;
    }

    public record Posicao(
            double vtp,    // valor total pago (só COMPRAs)
            double qc,     // quantidade corrente (compras - vendas; nunca negativo)
            double vma,    // valor médio de aquisição
            double d,      // total dividendos recebidos
            double vac,    // último VAC conhecido
            double ptg,    // (vac * qc) + d
            double leb,    // (vac * qc) - vtp
            double lebPct, // (leb/vtp)*100
            double lea,    // leb + d
            double leaPct  // (lea/vtp)*100
    ) {}

    public Posicao calcular(int investimentoId) {
        List<AporteRv> aportes = aporteRepo.findByInvestimento(investimentoId);

        double vtp = 0, qtdCompra = 0, d = 0;
        for (AporteRv a : aportes) {
            if (a.getTipoOp() == TipoOperacaoRv.COMPRA) {
                vtp += a.getValor();
                qtdCompra += a.getQuantidade() != null ? a.getQuantidade() : 0;
            } else if (a.getTipoOp() == TipoOperacaoRv.DIVIDENDO) {
                d += a.getValor();
            }
        }

        double qc = calcularQc(aportes);
        double vma = qtdCompra > 0 ? vtp / qtdCompra : 0;

        Optional<VacMensal> vacOpt = vacRepo.findUltimo(investimentoId);
        double vac = vacOpt.map(VacMensal::getVac).orElse(0.0);

        double ptg = vac * qc + d;
        double leb = vac * qc - vtp;
        double lebPct = vtp > 0 ? (leb / vtp) * 100 : 0;
        double lea = leb + d;
        double leaPct = vtp > 0 ? (lea / vtp) * 100 : 0;

        return new Posicao(vtp, qc, vma, d, vac, ptg, leb, lebPct, lea, leaPct);
    }

    /** VMR = (vac_atual * qc) − (vac_anterior * qc) */
    public double calcularVmr(int investimentoId, int mesAtual, int anoAtual) {
        List<AporteRv> aportes = aporteRepo.findByInvestimento(investimentoId);
        double qc = calcularQc(aportes);

        Optional<VacMensal> atual = vacRepo.find(investimentoId, mesAtual, anoAtual);
        int mesAnt = mesAtual == 1 ? 12 : mesAtual - 1;
        int anoAnt = mesAtual == 1 ? anoAtual - 1 : anoAtual;
        Optional<VacMensal> anterior = vacRepo.find(investimentoId, mesAnt, anoAnt);

        double vacAtual = atual.map(VacMensal::getVac).orElse(0.0);
        double vacAnt = anterior.map(VacMensal::getVac).orElse(0.0);

        return (vacAtual - vacAnt) * qc;
    }

    private double calcularQc(List<AporteRv> aportes) {
        double compras = 0, vendas = 0;
        for (AporteRv a : aportes) {
            if (a.getTipoOp() == TipoOperacaoRv.COMPRA) compras += a.getQuantidade() != null ? a.getQuantidade() : 0;
            else if (a.getTipoOp() == TipoOperacaoRv.VENDA) vendas += a.getQuantidade() != null ? a.getQuantidade() : 0;
        }
        return Math.max(0, compras - vendas);
    }
}
