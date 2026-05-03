package br.investimentos.service;

import br.investimentos.model.Investimento;
import br.investimentos.model.VtaMensal;
import br.investimentos.repository.VtaMensalRepository;

import java.time.LocalDate;
import java.util.Optional;

public class SaldoService {

    private final VtaMensalRepository vtaRepo;
    private final TaxaService taxaService;
    private final RendimentoService rendimentoService;

    public SaldoService(VtaMensalRepository vtaRepo, TaxaService taxaService, RendimentoService rendimentoService) {
        this.vtaRepo = vtaRepo;
        this.taxaService = taxaService;
        this.rendimentoService = rendimentoService;
    }

    /**
     * Saldo para o mês/ano: VTA se disponível, senão último VTA + projeção.
     * Aporte mensal usado na projeção.
     */
    public double saldo(Investimento inv, int mes, int ano, double aporteMensal) {
        Optional<VtaMensal> vtaOpt = vtaRepo.find(inv.getId(), mes, ano);
        if (vtaOpt.isPresent()) return vtaOpt.get().getVta();

        // Projeta a partir do último VTA disponível
        Optional<VtaMensal> ultimo = vtaRepo.findUltimo(inv.getId());
        if (ultimo.isEmpty()) return 0;

        VtaMensal base = ultimo.get();
        double taxa = taxaMensal(inv, base);
        int n = mesesEntre(base.getPeriodoAno(), base.getPeriodoMes(), ano, mes);
        if (n <= 0) return base.getVta();

        return projetar(base.getVta(), taxa, aporteMensal, n);
    }

    /** Saldo atual (mês corrente). */
    public double saldoAtual(Investimento inv, double aporteMensal) {
        LocalDate hoje = LocalDate.now();
        return saldo(inv, hoje.getMonthValue(), hoje.getYear(), aporteMensal);
    }

    private double taxaMensal(Investimento inv, VtaMensal base) {
        if (inv.getTaxaAnual() != null) {
            return taxaService.taxaMensalExplicita(inv.getTaxaAnual());
        }
        double r = rendimentoService.calcularR(base);
        double vi = rendimentoService.calcularVi(base.getInvestimentoId(), base.getPeriodoAno(), base.getPeriodoMes());
        double ta = taxaService.taxaAcumulada(r, vi);
        return taxaService.taxaMensalAcumulada(ta, base.getPeriodoMes());
    }

    public static double projetar(double saldo, double taxa, double aporte, int nMeses) {
        if (taxa == 0) return saldo + aporte * nMeses;
        double fator = Math.pow(1 + taxa, nMeses);
        return saldo * fator + aporte * (fator - 1) / taxa;
    }

    private int mesesEntre(int anoBase, int mesBase, int anoAlvo, int mesAlvo) {
        return (anoAlvo - anoBase) * 12 + (mesAlvo - mesBase);
    }
}
