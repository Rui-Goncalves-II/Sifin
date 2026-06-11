package br.investimentos.service;

import br.investimentos.model.VacMensal;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.InvestimentoRepository;
import br.investimentos.repository.VacMensalRepository;
import javafx.application.Platform;

import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class VacAutoService {

    private final InvestimentoRepository invRepo;
    private final VacMensalRepository vacRepo;
    private final BrapiService brapiSvc;

    public VacAutoService(InvestimentoRepository invRepo, VacMensalRepository vacRepo, BrapiService brapiSvc) {
        this.invRepo = invRepo;
        this.vacRepo = vacRepo;
        this.brapiSvc = brapiSvc;
    }

    public void atualizarVacsAsync(Consumer<String> onConcluido) {
        var executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vac-auto-refresh");
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            var investimentos = invRepo.findByTipo(TipoInvestimento.RENDA_VARIAVEL);
            LocalDate hoje = LocalDate.now();
            int atualizados = 0;
            for (var inv : investimentos) {
                String notas = inv.getNotas();
                if (notas == null || notas.isBlank()) continue;
                String ticker = notas.strip().split("\\s+")[0].toUpperCase();
                var precoOpt = brapiSvc.buscarPreco(ticker);
                if (precoOpt.isEmpty()) continue;
                VacMensal vac = new VacMensal();
                vac.setInvestimentoId(inv.getId());
                vac.setPeriodoMes(hoje.getMonthValue());
                vac.setPeriodoAno(hoje.getYear());
                vac.setVac(precoOpt.get());
                vac.setFonte("BRAPI");
                vacRepo.salvar(vac);
                atualizados++;
            }
            if (atualizados > 0) {
                final int count = atualizados;
                Platform.runLater(() -> onConcluido.accept(count + " ativo(s) atualizados"));
            }
            executor.shutdown();
        });
    }
}
