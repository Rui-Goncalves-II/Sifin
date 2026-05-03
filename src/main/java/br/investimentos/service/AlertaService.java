package br.investimentos.service;

import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.InvestimentoRepository;
import br.investimentos.repository.VacMensalRepository;
import br.investimentos.repository.VtaMensalRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AlertaService {

    private final InvestimentoRepository invRepo;
    private final VtaMensalRepository vtaRepo;
    private final VacMensalRepository vacRepo;

    public AlertaService(InvestimentoRepository invRepo, VtaMensalRepository vtaRepo, VacMensalRepository vacRepo) {
        this.invRepo = invRepo;
        this.vtaRepo = vtaRepo;
        this.vacRepo = vacRepo;
    }

    public List<String> alertasPendentes() {
        LocalDate hoje = LocalDate.now();
        int mes = hoje.getMonthValue();
        int ano = hoje.getYear();
        String periodo = String.format("%02d/%d", mes, ano);

        List<String> alertas = new ArrayList<>();

        for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_FIXA)) {
            if (vtaRepo.find(inv.getId(), mes, ano).isEmpty()) {
                alertas.add("⚠ " + inv.getNome() + ": VTA de " + periodo + " não informado.");
            }
        }

        for (Investimento inv : invRepo.findByTipo(TipoInvestimento.RENDA_VARIAVEL)) {
            var vacOpt = vacRepo.find(inv.getId(), mes, ano);
            // VAC de fonte API suprime o alerta
            if (vacOpt.isEmpty() || !"API".equals(vacOpt.get().getFonte())) {
                if (vacOpt.isEmpty()) {
                    alertas.add("⚠ " + inv.getNome() + ": VAC de " + periodo + " não atualizado.");
                }
            }
        }

        return alertas;
    }
}
