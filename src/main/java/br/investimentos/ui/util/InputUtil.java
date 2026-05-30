package br.investimentos.ui.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.util.function.UnaryOperator;

public class InputUtil {

    /**
     * Restringe o campo a dígitos e vírgula decimal (padrão BR).
     * Ponto é convertido automaticamente em vírgula.
     * Aceita opcional sinal negativo no início.
     */
    public static void applyDecimalFilter(TextField field) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (change.getText().contains(".")) {
                change.setText(change.getText().replace(".", ","));
            }
            if (change.getControlNewText().matches("-?[0-9]*,?[0-9]*")) {
                return change;
            }
            return null;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    /** Restringe o campo a dígitos inteiros (sem vírgula, sem letras). */
    public static void applyIntegerFilter(TextField field) {
        UnaryOperator<TextFormatter.Change> filter = change ->
            change.getControlNewText().matches("[0-9]*") ? change : null;
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    /** Converte toda entrada para maiúsculas em tempo real. */
    public static void applyUpperCaseFilter(TextField field) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            change.setText(change.getText().toUpperCase());
            return change;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    /** Converte string com vírgula decimal (padrão BR) para ponto, pronto para parse. */
    public static String normalizeDecimalInput(String text) {
        return text.strip().replace(",", ".");
    }

    /** Interpreta string com vírgula decimal (padrão BR) como double. */
    public static double parseDoubleField(String text) {
        return Double.parseDouble(normalizeDecimalInput(text));
    }
}
