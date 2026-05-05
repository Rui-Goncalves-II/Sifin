package br.investimentos.ui.util;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

public class Toast {

    public static void show(StackPane overlay, String titulo, String mensagem) {
        Label titleLbl = new Label(titulo);
        titleLbl.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 17px; -fx-font-weight: bold;");

        Label msgLbl = new Label(mensagem);
        msgLbl.setStyle("-fx-text-fill: #7d8fa0; -fx-font-size: 16px;");
        msgLbl.setWrapText(true);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #7d8fa0; " +
            "-fx-font-size: 15px; -fx-padding: 0 2 0 8; -fx-cursor: hand;");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(8, titleLbl, gap, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(6, header, msgLbl);
        card.getStyleClass().add("toast-card");
        card.setMaxWidth(300);
        card.setMinWidth(260);

        StackPane.setAlignment(card, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(card, new Insets(0, 24, 24, 0));
        overlay.getChildren().add(card);

        // Entrada: desliza da direita + fade
        card.setOpacity(0);
        card.setTranslateX(40);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), card);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(220), card);
        slideIn.setFromX(40); slideIn.setToX(0);
        ParallelTransition enter = new ParallelTransition(fadeIn, slideIn);

        PauseTransition wait = new PauseTransition(Duration.seconds(5));

        SequentialTransition seq = new SequentialTransition(enter, wait);
        seq.setOnFinished(e -> dismiss(overlay, card));
        seq.play();

        closeBtn.setOnAction(e -> { seq.stop(); dismiss(overlay, card); });
    }

    private static void dismiss(StackPane overlay, VBox card) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), card);
        fadeOut.setFromValue(card.getOpacity()); fadeOut.setToValue(0);
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(220), card);
        slideOut.setFromX(0); slideOut.setToX(40);
        ParallelTransition exit = new ParallelTransition(fadeOut, slideOut);
        exit.setOnFinished(e -> overlay.getChildren().remove(card));
        exit.play();
    }
}
