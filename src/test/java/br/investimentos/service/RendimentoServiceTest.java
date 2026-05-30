package br.investimentos.service;

import br.investimentos.model.Movimentacao;
import br.investimentos.model.VtaMensal;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.repository.MovimentacaoRepository;
import br.investimentos.repository.VaiAnualRepository;
import br.investimentos.repository.VtaMensalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RendimentoServiceTest {

    @Mock VaiAnualRepository vaiRepo;
    @Mock MovimentacaoRepository movRepo;
    @Mock VtaMensalRepository vtaRepo;

    private RendimentoService svc;

    @BeforeEach
    void setUp() { svc = new RendimentoService(vaiRepo, movRepo, vtaRepo); }

    @Test
    void calcularSam_soDepositos() {
        Movimentacao dep = mov(TipoMovimentacao.DEPOSITO, 1000.0);
        when(movRepo.findByInvestimentoAnoMesAte(1, 2025, 6)).thenReturn(List.of(dep));

        assertEquals(1000.0, svc.calcularSam(1, 2025, 6), 1e-10);
    }

    @Test
    void calcularSam_depositoMenosSaque() {
        Movimentacao dep = mov(TipoMovimentacao.DEPOSITO, 2000.0);
        Movimentacao saq = mov(TipoMovimentacao.SAQUE, 500.0);
        when(movRepo.findByInvestimentoAnoMesAte(1, 2025, 6)).thenReturn(List.of(dep, saq));

        assertEquals(1500.0, svc.calcularSam(1, 2025, 6), 1e-10);
    }

    @Test
    void calcularSam_semMovimentacoes_retornaZero() {
        when(movRepo.findByInvestimentoAnoMesAte(1, 2025, 6)).thenReturn(List.of());

        assertEquals(0.0, svc.calcularSam(1, 2025, 6), 1e-10);
    }

    @Test
    void calcularVi_vaiMaisSam() {
        when(vaiRepo.getVai(1, 2025)).thenReturn(5000.0);
        when(movRepo.findByInvestimentoAnoMesAte(1, 2025, 3)).thenReturn(
                List.of(mov(TipoMovimentacao.DEPOSITO, 1000.0)));

        assertEquals(6000.0, svc.calcularVi(1, 2025, 3), 1e-10);
    }

    @Test
    void calcularR_vtaMensal_rendimentoPositivo() {
        VtaMensal vta = vtaMensal(1, 3, 2025, 6500.0);
        when(vaiRepo.getVai(1, 2025)).thenReturn(5000.0);
        when(movRepo.findByInvestimentoAnoMesAte(1, 2025, 3)).thenReturn(
                List.of(mov(TipoMovimentacao.DEPOSITO, 1000.0)));

        // VI = 6000, VTA = 6500 → R = 500
        assertEquals(500.0, svc.calcularR(vta), 1e-10);
    }

    @Test
    void calcularR_vtaMensal_rendimentoNegativo() {
        VtaMensal vta = vtaMensal(1, 3, 2025, 5800.0);
        when(vaiRepo.getVai(1, 2025)).thenReturn(5000.0);
        when(movRepo.findByInvestimentoAnoMesAte(1, 2025, 3)).thenReturn(
                List.of(mov(TipoMovimentacao.DEPOSITO, 1000.0)));

        // VI = 6000, VTA = 5800 → R = -200
        assertEquals(-200.0, svc.calcularR(vta), 1e-10);
    }

    @Test
    void calcularR_porIdMesAno_semVta_retornaZero() {
        when(vtaRepo.find(1, 6, 2025)).thenReturn(Optional.empty());

        assertEquals(0.0, svc.calcularR(1, 6, 2025), 1e-10);
    }

    @Test
    void calcularR_porIdMesAno_comVta() {
        VtaMensal vta = vtaMensal(1, 6, 2025, 10500.0);
        when(vtaRepo.find(1, 6, 2025)).thenReturn(Optional.of(vta));
        when(vaiRepo.getVai(1, 2025)).thenReturn(10000.0);
        when(movRepo.findByInvestimentoAnoMesAte(1, 2025, 6)).thenReturn(List.of());

        assertEquals(500.0, svc.calcularR(1, 6, 2025), 1e-10);
    }


    // -- helpers --

    private Movimentacao mov(TipoMovimentacao tipo, double valor) {
        Movimentacao m = new Movimentacao();
        m.setTipoMov(tipo);
        m.setValor(valor);
        return m;
    }

    private VtaMensal vtaMensal(int invId, int mes, int ano, double vta) {
        VtaMensal v = new VtaMensal();
        v.setInvestimentoId(invId);
        v.setPeriodoMes(mes);
        v.setPeriodoAno(ano);
        v.setVta(vta);
        return v;
    }
}
