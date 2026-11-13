package br.investimentos.service;

import br.investimentos.model.AporteRv;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.VacMensal;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.model.enums.TipoOperacaoRv;

import java.util.List;
import java.util.Optional;

/**
 * Crescimento do patrimônio investido de um ativo no ano:
 * <pre>
 *   base     = valor no fim do ano anterior + aportes líquidos do ano
 *   cresc. % = (valor atual − base) / base × 100
 * </pre>
 * Retorno simples sobre o capital (não pondera a data dos aportes).
 * Renda variável ignora dividendos. Funções puras: os painéis carregam os dados.
 */
public final class CrescimentoService {

    public static final int ANO_TODOS = -1;

    private CrescimentoService() {}

    /** {@code pct} é null quando não há base positiva ou valor atual conhecido. */
    public record Crescimento(double valorAnterior, double aportesLiquidos, Double valorAtual, Double pct) {
        public double base() { return valorAnterior + aportesLiquidos; }
        public Double diferenca() { return valorAtual == null ? null : valorAtual - base(); }
    }

    public static Crescimento calcular(double valorAnterior, double aportesLiquidos, Double valorAtual) {
        double base = valorAnterior + aportesLiquidos;
        Double pct = (valorAtual != null && base > 0) ? (valorAtual - base) / base * 100 : null;
        return new Crescimento(valorAnterior, aportesLiquidos, valorAtual, pct);
    }

    /**
     * Consolida vários ativos somando valores (não percentuais): o % resultante é ponderado
     * pelo tamanho de cada posição. Ativos sem valor atual conhecido são excluídos por inteiro
     * (base e valor), para não distorcer o total.
     */
    public static Crescimento somar(List<Crescimento> itens) {
        double anterior = 0, aportes = 0, atual = 0;
        boolean algum = false;
        for (Crescimento c : itens) {
            if (c.valorAtual() == null) continue;
            anterior += c.valorAnterior();
            aportes += c.aportesLiquidos();
            atual += c.valorAtual();
            algum = true;
        }
        return calcular(anterior, aportes, algum ? atual : null);
    }

    // ── Renda Fixa ───────────────────────────────────────────────────────

    /**
     * @param vai          VAI do ano (último VTA do ano anterior); 0 no modo Total
     * @param sam          depósitos − saques do ano (ou de todo o histórico no modo Total)
     * @param vtaAtual     último VTA do ano (ou último VTA geral no modo Total); null se não houver
     */
    public static Crescimento rendaFixa(double vai, double sam, Double vtaAtual) {
        return calcular(vai, sam, vtaAtual);
    }

    // ── Renda Variável ───────────────────────────────────────────────────

    public static Crescimento rendaVariavel(List<AporteRv> aportes, List<VacMensal> vacs, int ano) {
        if (ano == ANO_TODOS) {
            double liquido = aportesLiquidosRv(aportes, Integer.MIN_VALUE, Integer.MAX_VALUE);
            double qc = qcAte(aportes, Integer.MAX_VALUE);
            Double atual = vacAte(vacs, Integer.MAX_VALUE).map(v -> v * qc).orElse(qc == 0 ? 0.0 : null);
            return calcular(0, liquido, atual);
        }

        double qcAnterior = qcAte(aportes, ano - 1);
        double anterior = 0;
        if (qcAnterior > 0) {
            // Sem VAC até o fim do ano anterior: usa o custo líquido das cotas como estimativa.
            anterior = vacAte(vacs, ano - 1).map(v -> v * qcAnterior)
                    .orElse(Math.max(0, aportesLiquidosRv(aportes, Integer.MIN_VALUE, ano - 1)));
        }
        double aportesAno = aportesLiquidosRv(aportes, ano, ano);
        double qcAtual = qcAte(aportes, ano);
        Double atual = vacAte(vacs, ano).map(v -> v * qcAtual).orElse(qcAtual == 0 ? 0.0 : null);
        return calcular(anterior, aportesAno, atual);
    }

    /** Σ COMPRA − Σ VENDA (valor em R$) entre os anos informados, inclusive. Dividendos ignorados. */
    static double aportesLiquidosRv(List<AporteRv> aportes, int anoDe, int anoAte) {
        double total = 0;
        for (AporteRv a : aportes) {
            if (a.getPeriodoAno() < anoDe || a.getPeriodoAno() > anoAte) continue;
            if (a.getTipoOp() == TipoOperacaoRv.COMPRA) total += a.getValor();
            else if (a.getTipoOp() == TipoOperacaoRv.VENDA) total -= a.getValor();
        }
        return total;
    }

    static double qcAte(List<AporteRv> aportes, int anoAte) {
        double qc = 0;
        for (AporteRv a : aportes) {
            if (a.getPeriodoAno() > anoAte || a.getQuantidade() == null) continue;
            if (a.getTipoOp() == TipoOperacaoRv.COMPRA) qc += a.getQuantidade();
            else if (a.getTipoOp() == TipoOperacaoRv.VENDA) qc -= a.getQuantidade();
        }
        return Math.max(0, qc);
    }

    /** Último VAC registrado até dezembro do ano informado. */
    static Optional<Double> vacAte(List<VacMensal> vacs, int anoAte) {
        VacMensal ultimo = null;
        for (VacMensal v : vacs) {
            if (v.getPeriodoAno() > anoAte) continue;
            if (ultimo == null || v.getPeriodoAno() > ultimo.getPeriodoAno()
                    || (v.getPeriodoAno() == ultimo.getPeriodoAno() && v.getPeriodoMes() > ultimo.getPeriodoMes()))
                ultimo = v;
        }
        return Optional.ofNullable(ultimo).map(VacMensal::getVac);
    }

    // ── Dólar ────────────────────────────────────────────────────────────

    /**
     * @param cotFimAnoAnterior cotação em 31/12 do ano anterior (null se não houver registro)
     * @param cotAtual          cotação usada para o valor atual (atual se ano corrente, senão 31/12 do ano)
     * @param cotFallback       cotação usada para movimentações sem cotação registrada
     */
    public static Crescimento dolar(List<Movimentacao> movs, int ano, Double cotFimAnoAnterior,
                                    Double cotAtual, double cotFallback) {
        if (ano == ANO_TODOS) {
            double liquido = custoBrl(movs, Integer.MIN_VALUE, Integer.MAX_VALUE, cotFallback);
            double usd = saldoUsdAte(movs, Integer.MAX_VALUE);
            return calcular(0, liquido, valorBrl(usd, cotAtual));
        }

        double usdAnterior = saldoUsdAte(movs, ano - 1);
        double anterior = 0;
        if (usdAnterior > 0) {
            anterior = cotFimAnoAnterior != null
                    ? usdAnterior * cotFimAnoAnterior
                    : Math.max(0, custoBrl(movs, Integer.MIN_VALUE, ano - 1, cotFallback));
        }
        double aportesAno = custoBrl(movs, ano, ano, cotFallback);
        double usdAtual = saldoUsdAte(movs, ano);
        return calcular(anterior, aportesAno, valorBrl(usdAtual, cotAtual));
    }

    private static Double valorBrl(double usd, Double cot) {
        if (usd <= 0) return 0.0;
        return (cot != null && cot > 0) ? usd * cot : null;
    }

    static double saldoUsdAte(List<Movimentacao> movs, int anoAte) {
        double usd = 0;
        for (Movimentacao m : movs) {
            if (m.getPeriodoAno() > anoAte) continue;
            usd += m.getTipoMov() == TipoMovimentacao.DEPOSITO ? m.getValor() : -m.getValor();
        }
        return usd;
    }

    /** Σ(DEP × cot) − Σ(SAQ × cot) entre os anos informados; movimentações sem cotação usam o fallback. */
    static double custoBrl(List<Movimentacao> movs, int anoDe, int anoAte, double cotFallback) {
        double total = 0;
        for (Movimentacao m : movs) {
            if (m.getPeriodoAno() < anoDe || m.getPeriodoAno() > anoAte) continue;
            double cot = m.getCotacaoDolar() != null ? m.getCotacaoDolar() : cotFallback;
            total += (m.getTipoMov() == TipoMovimentacao.DEPOSITO ? 1 : -1) * m.getValor() * cot;
        }
        return total;
    }
}
