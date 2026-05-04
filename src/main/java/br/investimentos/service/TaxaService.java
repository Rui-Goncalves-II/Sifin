package br.investimentos.service;

public class TaxaService {

    /** Taxa mensal a partir de taxa anual explícita. Prioridade absoluta. */
    public double taxaMensalExplicita(double taxaAnualPct) {
        return Math.pow(1.0 + taxaAnualPct / 100.0, 1.0 / 12.0) - 1.0;
    }

    /** Taxa mensal a partir de taxa acumulada no ano e número de meses com VTA. */
    public double taxaMensalAcumulada(double taxaAcumulada, int nMeses) {
        if (nMeses <= 0) return 0;
        double base = 1.0 + taxaAcumulada;
        // base negativa (VI negativo por saque > VAI) → Math.pow retornaria NaN
        if (base <= 0) return 0;
        return Math.pow(base, 1.0 / nMeses) - 1.0;
    }

    /** Taxa acumulada no período: R / VI. */
    public double taxaAcumulada(double r, double vi) {
        if (vi == 0) return 0;
        return r / vi;
    }
}
