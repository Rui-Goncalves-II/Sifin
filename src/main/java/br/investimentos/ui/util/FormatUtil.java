package br.investimentos.ui.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class FormatUtil {

    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final Locale PT_BR = new Locale("pt", "BR");

    private FormatUtil() {}

    public static String brl(double value) {
        if (!Double.isFinite(value)) return "—";
        return CURRENCY.format(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP));
    }

    public static String pct(double value) {
        return String.format(PT_BR, "%.2f%%", value);
    }

    public static String qtd(double value) {
        return String.format(PT_BR, "%.4f", value);
    }

    public static String numero(double value, int decimais) {
        return String.format(PT_BR, "%." + decimais + "f", value);
    }

    public static String mesAno(int mes, int ano) {
        return String.format("%02d/%d", mes, ano);
    }

    public static String sinalBrl(double value) {
        return (value >= 0 ? "▲ " : "▼ ") + brl(Math.abs(value));
    }

    public static String sinalPct(double value) {
        return (value >= 0 ? "▲ " : "▼ ") + pct(Math.abs(value));
    }

    public static String brlAbrev(double value) {
        if (value >= 1_000_000) return String.format(PT_BR, "R$ %.2fM", value / 1_000_000);
        if (value >= 1_000)     return String.format(PT_BR, "R$ %.1fk", value / 1_000);
        return brl(value);
    }

    public static double headerW(String title) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(title);
        t.setFont(javafx.scene.text.Font.font("SansSerif", javafx.scene.text.FontWeight.BOLD, 11));
        return Math.ceil(t.getBoundsInLocal().getWidth()) + 32;
    }
}
