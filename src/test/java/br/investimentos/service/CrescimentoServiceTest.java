package br.investimentos.service;

import br.investimentos.model.AporteRv;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.VacMensal;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.model.enums.TipoOperacaoRv;
import br.investimentos.service.CrescimentoService.Crescimento;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrescimentoServiceTest {

    // ── Base ─────────────────────────────────────────────────────────────

    @Test
    void calcular_ganho() {
        Crescimento c = CrescimentoService.calcular(10_000, 2_000, 13_200.0);
        assertEquals(12_000, c.base(), 1e-9);
        assertEquals(10.0, c.pct(), 1e-9);
    }

    @Test
    void calcular_baseZeroOuNegativa_semPct() {
        assertNull(CrescimentoService.calcular(0, 0, 0.0).pct());
        assertNull(CrescimentoService.calcular(1_000, -2_000, 50.0).pct());
    }

    @Test
    void calcular_semValorAtual_semPct() {
        assertNull(CrescimentoService.calcular(1_000, 0, null).pct());
    }

    @Test
    void somar_ponderaPorValor_eIgnoraSemValorAtual() {
        Crescimento c = CrescimentoService.somar(List.of(
                CrescimentoService.calcular(0, 100, 150.0),        // +50%
                CrescimentoService.calcular(0, 50_000, 47_500.0),  // −5%
                CrescimentoService.calcular(0, 1_000, null)));     // excluído
        assertEquals(50_100, c.base(), 1e-9);
        assertEquals((47_650 - 50_100) / 50_100.0 * 100, c.pct(), 1e-9);
    }

    // ── Renda Fixa ───────────────────────────────────────────────────────

    @Test
    void rendaFixa_perda() {
        Crescimento c = CrescimentoService.rendaFixa(5_000, 1_000, 5_700.0);
        assertEquals(-5.0, c.pct(), 1e-9);
    }

    // ── Renda Variável ───────────────────────────────────────────────────

    @Test
    void rv_anoComCompraEVenda_descontaVenda() {
        List<AporteRv> ops = List.of(
                rv(TipoOperacaoRv.COMPRA, 2024, 10, 1_000),   // 10 cotas a 100
                rv(TipoOperacaoRv.COMPRA, 2025, 5, 600),      // 5 cotas a 120
                rv(TipoOperacaoRv.VENDA, 2025, 3, 390),       // 3 cotas a 130
                rv(TipoOperacaoRv.DIVIDENDO, 2025, null, 50)); // ignorado
        List<VacMensal> vacs = List.of(vac(2024, 12, 110), vac(2025, 6, 130));

        Crescimento c = CrescimentoService.rendaVariavel(ops, vacs, 2025);

        assertEquals(1_100, c.valorAnterior(), 1e-9);       // 10 × 110
        assertEquals(210, c.aportesLiquidos(), 1e-9);       // 600 − 390
        assertEquals(12 * 130, c.valorAtual(), 1e-9);       // QC 12 × 130
        assertEquals((1_560 - 1_310) / 1_310.0 * 100, c.pct(), 1e-9);
    }

    @Test
    void rv_semVacNoAnoAnterior_usaCusto() {
        List<AporteRv> ops = List.of(rv(TipoOperacaoRv.COMPRA, 2024, 10, 1_000));
        List<VacMensal> vacs = List.of(vac(2025, 3, 120));

        Crescimento c = CrescimentoService.rendaVariavel(ops, vacs, 2025);

        assertEquals(1_000, c.valorAnterior(), 1e-9);
        assertEquals(20.0, c.pct(), 1e-9);
    }

    @Test
    void rv_semVac_semPct() {
        Crescimento c = CrescimentoService.rendaVariavel(
                List.of(rv(TipoOperacaoRv.COMPRA, 2025, 10, 1_000)), List.of(), 2025);
        assertNull(c.valorAtual());
        assertNull(c.pct());
    }

    @Test
    void rv_total() {
        List<AporteRv> ops = List.of(
                rv(TipoOperacaoRv.COMPRA, 2024, 10, 1_000),
                rv(TipoOperacaoRv.COMPRA, 2025, 10, 1_000));
        Crescimento c = CrescimentoService.rendaVariavel(ops, List.of(vac(2025, 1, 90)), CrescimentoService.ANO_TODOS);
        assertEquals(-10.0, c.pct(), 1e-9);
    }

    // ── Dólar ────────────────────────────────────────────────────────────

    @Test
    void dolar_ano() {
        List<Movimentacao> movs = List.of(
                mov(TipoMovimentacao.DEPOSITO, 2024, 1_000, 5.0),
                mov(TipoMovimentacao.DEPOSITO, 2025, 500, 5.5),
                mov(TipoMovimentacao.SAQUE, 2025, 100, null));   // fallback 6.0

        Crescimento c = CrescimentoService.dolar(movs, 2025, 5.2, 6.0, 6.0);

        assertEquals(1_000 * 5.2, c.valorAnterior(), 1e-9);
        assertEquals(500 * 5.5 - 100 * 6.0, c.aportesLiquidos(), 1e-9);
        assertEquals(1_400 * 6.0, c.valorAtual(), 1e-9);
    }

    @Test
    void dolar_semCotacaoFimAnoAnterior_usaCusto() {
        List<Movimentacao> movs = List.of(mov(TipoMovimentacao.DEPOSITO, 2024, 1_000, 5.0));
        Crescimento c = CrescimentoService.dolar(movs, 2025, null, 5.5, 5.5);
        assertEquals(5_000, c.valorAnterior(), 1e-9);
        assertEquals(10.0, c.pct(), 1e-9);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static AporteRv rv(TipoOperacaoRv tipo, int ano, Integer qtd, double valor) {
        AporteRv a = new AporteRv();
        a.setTipoOp(tipo);
        a.setPeriodoAno(ano);
        a.setPeriodoMes(1);
        a.setQuantidade(qtd == null ? null : qtd.doubleValue());
        a.setValor(valor);
        return a;
    }

    private static VacMensal vac(int ano, int mes, double valor) {
        VacMensal v = new VacMensal();
        v.setPeriodoAno(ano);
        v.setPeriodoMes(mes);
        v.setVac(valor);
        return v;
    }

    private static Movimentacao mov(TipoMovimentacao tipo, int ano, double valor, Double cot) {
        Movimentacao m = new Movimentacao();
        m.setTipoMov(tipo);
        m.setPeriodoAno(ano);
        m.setPeriodoMes(1);
        m.setValor(valor);
        m.setCotacaoDolar(cot);
        return m;
    }
}
