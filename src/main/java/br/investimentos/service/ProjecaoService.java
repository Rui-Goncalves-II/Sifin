package br.investimentos.service;

import br.investimentos.model.Investimento;
import br.investimentos.model.VtaMensal;
import br.investimentos.repository.VtaMensalRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProjecaoService {

    private final VtaMensalRepository vtaRepo;
    private final TaxaService taxaService;
    private final RendimentoService rendimentoService;

    public record PontoProjecao(int mes, int ano, double pessimista, double realista, double otimista) {}

    public ProjecaoService(VtaMensalRepository vtaRepo, TaxaService taxaService, RendimentoService rendimentoService) {
        this.vtaRepo = vtaRepo;
        this.taxaService = taxaService;
        this.rendimentoService = rendimentoService;
    }

    public List<PontoProjecao> projetar(Investimento inv, double aporteMensal, int nMeses) {
        Optional<VtaMensal> ultimoOpt = vtaRepo.findUltimo(inv.getId());
        double saldoBase = ultimoOpt.map(VtaMensal::getVta).orElse(0.0);

        double taxa = 0;
        if (inv.getTaxaAnual() != null) {
            taxa = taxaService.taxaMensalExplicita(inv.getTaxaAnual());
        } else if (ultimoOpt.isPresent()) {
            VtaMensal base = ultimoOpt.get();
            double r = rendimentoService.calcularR(base);
            double vi = rendimentoService.calcularVi(base.getInvestimentoId(), base.getPeriodoAno(), base.getPeriodoMes());
            double ta = taxaService.taxaAcumulada(r, vi);
            taxa = taxaService.taxaMensalAcumulada(ta, base.getPeriodoMes());
        }

        int mesBase = ultimoOpt.map(VtaMensal::getPeriodoMes).orElse(java.time.LocalDate.now().getMonthValue());
        int anoBase = ultimoOpt.map(VtaMensal::getPeriodoAno).orElse(java.time.LocalDate.now().getYear());

        List<PontoProjecao> pontos = new ArrayList<>();
        for (int n = 1; n <= nMeses; n++) {
            int mesAlvo = ((mesBase - 1 + n) % 12) + 1;
            int anoAlvo = anoBase + (mesBase - 1 + n) / 12;

            // 3 cenários: taxa e aporte × 0.8 / 1.0 / 1.2
            double pess = SaldoService.projetar(saldoBase, taxa * 0.8, aporteMensal * 0.8, n);
            double real = SaldoService.projetar(saldoBase, taxa, aporteMensal, n);
            double otim = SaldoService.projetar(saldoBase, taxa * 1.2, aporteMensal * 1.2, n);

            pontos.add(new PontoProjecao(mesAlvo, anoAlvo, pess, real, otim));
        }
        return pontos;
    }
}
