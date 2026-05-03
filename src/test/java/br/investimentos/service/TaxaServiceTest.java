package br.investimentos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaxaServiceTest {

    private TaxaService svc;

    @BeforeEach
    void setUp() { svc = new TaxaService(); }

    @Test
    void taxaMensalExplicita_12pct_correta() {
        double mensal = svc.taxaMensalExplicita(12.0);
        // (1.12)^(1/12) - 1 ≈ 0.009489
        assertEquals(0.009489, mensal, 0.000001);
    }

    @Test
    void taxaMensalExplicita_zero_retornaZero() {
        assertEquals(0.0, svc.taxaMensalExplicita(0.0), 1e-10);
    }

    @Test
    void taxaAcumulada_viZero_retornaZero() {
        assertEquals(0.0, svc.taxaAcumulada(100, 0), 1e-10);
    }

    @Test
    void taxaAcumulada_calculo_correto() {
        // R=50, VI=1000 → 0.05
        assertEquals(0.05, svc.taxaAcumulada(50, 1000), 1e-10);
    }

    @Test
    void taxaMensalAcumulada_nMesesZero_retornaZero() {
        assertEquals(0.0, svc.taxaMensalAcumulada(0.05, 0), 1e-10);
    }

    @Test
    void taxaMensalAcumulada_1mes_igualAcumulada() {
        double ta = 0.05;
        double mensal = svc.taxaMensalAcumulada(ta, 1);
        assertEquals(ta, mensal, 1e-10);
    }

    @Test
    void taxaMensalAcumulada_12meses_consistente_com_anual() {
        // taxa acumulada de 12% ao ano: mensal ≈ 0.9489%
        double ta = 0.12;
        double mensal = svc.taxaMensalAcumulada(ta, 12);
        double recomposto = Math.pow(1 + mensal, 12) - 1;
        assertEquals(ta, recomposto, 1e-10);
    }
}
