package br.investimentos.service;

import br.investimentos.model.AporteRv;
import br.investimentos.model.Investimento;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.model.enums.TipoOperacaoRv;
import br.investimentos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsolidacaoServiceTest {

    @Mock InvestimentoRepository invRepo;
    @Mock MovimentacaoRepository movRepo;
    @Mock VtaMensalRepository vtaRepo;
    @Mock VaiAnualRepository vaiRepo;
    @Mock AporteRvRepository aporteRepo;
    @Mock VacMensalRepository vacRepo;
    @Mock RendimentoService rendimentoService;
    @Mock RendaVariavelService rvService;

    private ConsolidacaoService svc;

    private final Investimento rf = inv(1, TipoInvestimento.RENDA_FIXA);
    private final Investimento rv = inv(2, TipoInvestimento.RENDA_VARIAVEL);
    private final Investimento usd = inv(3, TipoInvestimento.DOLAR);

    @BeforeEach
    void setUp() {
        svc = new ConsolidacaoService(invRepo, movRepo, vtaRepo, vaiRepo, aporteRepo, vacRepo, rendimentoService, rvService);
        when(invRepo.findAll()).thenReturn(List.of(rf, rv, usd));
    }

    @Test
    void aportesPorTipo_valoresLiquidosPorMes() {
        when(movRepo.findByInvestimentoEAno(1, 2025)).thenReturn(List.of(
                mov(TipoMovimentacao.DEPOSITO, 1000, 3, null),
                mov(TipoMovimentacao.SAQUE, 1500, 3, null),
                mov(TipoMovimentacao.DEPOSITO, 200, 5, null)));
        when(aporteRepo.findByInvestimentoEAno(2, 2025)).thenReturn(List.of(
                ap(TipoOperacaoRv.COMPRA, 800, 3),
                ap(TipoOperacaoRv.VENDA, 300, 3),
                ap(TipoOperacaoRv.DIVIDENDO, 50, 3),
                ap(TipoOperacaoRv.DIVIDENDO, 70, 7)));
        when(movRepo.findByInvestimentoEAno(3, 2025)).thenReturn(List.of(
                mov(TipoMovimentacao.DEPOSITO, 100, 3, 5.0),
                mov(TipoMovimentacao.SAQUE, 10, 5, null)));

        var res = svc.calcularAportesPorMesPorTipo(2025, EnumSet.allOf(TipoInvestimento.class), 6.0);

        assertEquals(2, res.size(), "mês só com dividendo não deve aparecer");
        var mar = res.get(0);
        assertEquals(3, mar.mes());
        assertEquals(-500, mar.rf(), 1e-9);
        assertEquals(500, mar.rv(), 1e-9);
        assertEquals(500, mar.dolar(), 1e-9);   // 100 USD × 5,00 (cotação da mov.)
        assertEquals(500, mar.total(), 1e-9);
        var mai = res.get(1);
        assertEquals(5, mai.mes());
        assertEquals(200, mai.rf(), 1e-9);
        assertEquals(-60, mai.dolar(), 1e-9);   // 10 USD × 6,00 (fallback cotação atual)
    }

    @Test
    void aportesPorTipo_respeitaFiltroDeTipos() {
        when(movRepo.findByInvestimentoEAno(1, 2025)).thenReturn(List.of(mov(TipoMovimentacao.DEPOSITO, 1000, 1, null)));

        var res = svc.calcularAportesPorMesPorTipo(2025, EnumSet.of(TipoInvestimento.RENDA_FIXA), 6.0);

        assertEquals(1, res.size());
        assertEquals(1000, res.get(0).total(), 1e-9);
        verify(aporteRepo, never()).findByInvestimentoEAno(anyInt(), anyInt());
        verify(movRepo, never()).findByInvestimentoEAno(eq(3), anyInt());
    }

    private static Investimento inv(int id, TipoInvestimento tipo) {
        Investimento i = new Investimento();
        i.setId(id);
        i.setTipo(tipo);
        return i;
    }

    private static Movimentacao mov(TipoMovimentacao tipo, double valor, int mes, Double cotacao) {
        Movimentacao m = new Movimentacao();
        m.setTipoMov(tipo);
        m.setValor(valor);
        m.setPeriodoMes(mes);
        m.setPeriodoAno(2025);
        m.setCotacaoDolar(cotacao);
        return m;
    }

    private static AporteRv ap(TipoOperacaoRv tipo, double valor, int mes) {
        AporteRv a = new AporteRv();
        a.setTipoOp(tipo);
        a.setValor(valor);
        a.setPeriodoMes(mes);
        a.setPeriodoAno(2025);
        return a;
    }
}
