package br.investimentos.ui.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class AtualizacaoDialog {

    public static void show(Stage owner, String versaoAtual, String versaoNova, String urlRelease) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setResizable(false);

        // ── Header ──────────────────────────────────────────────────
        Label title = new Label("Nova versão disponível");
        title.getStyleClass().add("dialog-header-title");

        HBox header = new HBox(title);
        header.getStyleClass().add("dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // ── Body ─────────────────────────────────────────────────────
        Label versaoLbl = new Label("v" + versaoNova + " disponível  •  atual: v" + versaoAtual);
        versaoLbl.setStyle("-fx-text-fill: #7d8fa0; -fx-font-size: 15px;");

        Label descLbl = new Label("Deseja atualizar agora? O aplicativo será recompilado e reiniciado automaticamente.");
        descLbl.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 15px;");
        descLbl.setWrapText(true);

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(160);
        logArea.setStyle(
            "-fx-control-inner-background: #0d1117; -fx-text-fill: #adbac7; " +
            "-fx-font-family: monospace; -fx-font-size: 12px; -fx-border-color: #2a3441;");
        logArea.setVisible(false);
        logArea.setManaged(false);

        VBox body = new VBox(10, versaoLbl, descLbl, logArea);
        body.getStyleClass().add("dialog-body");
        body.setPrefWidth(480);

        // ── Footer ───────────────────────────────────────────────────
        Button btnDepois = new Button("Deixar para depois");
        btnDepois.setStyle(
            "-fx-background-color: #21262d; -fx-text-fill: #e6edf3; " +
            "-fx-border-color: #3a4252; -fx-border-width: 1; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-font-size: 14px; -fx-padding: 6 16; -fx-cursor: hand;");

        Button btnAtualizar = new Button("Atualizar");
        btnAtualizar.setStyle(
            "-fx-background-color: #238636; -fx-text-fill: white; " +
            "-fx-background-radius: 6; -fx-font-size: 14px; " +
            "-fx-padding: 6 16; -fx-cursor: hand;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, spacer, btnDepois, btnAtualizar);
        footer.getStyleClass().add("dialog-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        // ── Layout ───────────────────────────────────────────────────
        VBox root = new VBox(header, body, footer);
        root.setStyle("-fx-background-color: #161b22; -fx-border-color: #2a3441; -fx-border-width: 1;");

        Scene scene = new Scene(root);
        scene.getStylesheets().add(AtualizacaoDialog.class.getResource("/styles.css").toExternalForm());
        dialog.setScene(scene);

        // ── Acções ───────────────────────────────────────────────────
        btnDepois.setOnAction(e -> dialog.close());

        btnAtualizar.setOnAction(e ->
            executarAtualizacao(logArea, btnAtualizar, btnDepois, body, descLbl, dialog, urlRelease));

        dialog.show();
        if (owner != null) {
            dialog.setX(owner.getX() + (owner.getWidth()  - dialog.getWidth())  / 2);
            dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
        }
    }

    private static void executarAtualizacao(TextArea logArea, Button btnAtualizar,
                                             Button btnDepois, VBox body, Label descLbl,
                                             Stage dialog, String urlRelease) {
        String appHome = System.getProperty("app.home");

        if (appHome == null || !Files.isDirectory(Paths.get(appHome))) {
            // Fallback: sem app.home (execução pelo IDE) → abre browser
            abrirBrowser(urlRelease);
            dialog.close();
            return;
        }

        // Preparar UI para mostrar progresso
        btnAtualizar.setDisable(true);
        btnDepois.setDisable(true);
        btnAtualizar.setText("Atualizando…");
        descLbl.setText("Aguarde enquanto o aplicativo é atualizado…");
        logArea.setVisible(true);
        logArea.setManaged(true);
        dialog.sizeToScene();

        CompletableFuture.runAsync(() -> {
            boolean ok = runCmd(logArea, appHome, "git", "-C", appHome, "pull");
            if (ok) {
                appendLog(logArea, "\nCompilando…\n");
                ok = runCmd(logArea, appHome, "mvn", "-f", appHome + "/pom.xml",
                            "package", "-DskipTests", "-q");
            }

            final boolean sucesso = ok;
            Platform.runLater(() -> {
                if (sucesso) {
                    appendLog(logArea, "\nAtualização concluída. Reiniciando…\n");
                    relancar(appHome);
                } else {
                    appendLog(logArea, "\nFalha na atualização. Clique abaixo para baixar manualmente.\n");
                    btnAtualizar.setText("Ver release no GitHub");
                    btnAtualizar.setDisable(false);
                    btnAtualizar.setOnAction(ev -> { abrirBrowser(urlRelease); dialog.close(); });
                    btnDepois.setDisable(false);
                }
            });
        });
    }

    private static boolean runCmd(TextArea logArea, String workDir, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(Paths.get(workDir).toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    final String ln = line;
                    Platform.runLater(() -> appendLog(logArea, ln + "\n"));
                }
            }
            return proc.waitFor() == 0;
        } catch (Exception e) {
            Platform.runLater(() -> appendLog(logArea, "ERRO: " + e.getMessage() + "\n"));
            return false;
        }
    }

    private static void appendLog(TextArea area, String text) {
        area.appendText(text);
    }

    private static void relancar(String appHome) {
        try {
            // Procura o JAR em target/
            Path target = Paths.get(appHome, "target");
            Path jar = Files.list(target)
                .filter(p -> p.getFileName().toString().startsWith("sifin-") &&
                             p.getFileName().toString().endsWith(".jar") &&
                             !p.getFileName().toString().contains("original"))
                .findFirst()
                .orElse(null);

            if (jar == null) return;

            String javaExe = ProcessHandle.current().info().command().orElse("java");
            new ProcessBuilder(javaExe,
                    "-Dapp.home=" + appHome,
                    "-Dfile.encoding=UTF-8",
                    "-jar", jar.toString())
                .directory(Paths.get(appHome).toFile())
                .start();

            Platform.exit();
        } catch (Exception ignored) {
            Platform.exit();
        }
    }

    private static void abrirBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported())
                Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ignored) {}
    }
}
