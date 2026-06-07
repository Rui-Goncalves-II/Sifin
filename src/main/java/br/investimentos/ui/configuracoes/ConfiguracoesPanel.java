package br.investimentos.ui.configuracoes;

import br.investimentos.db.DatabaseManager;
import br.investimentos.service.BrapiService;
import br.investimentos.service.ConfigService;
import br.investimentos.ui.util.Toast;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class ConfiguracoesPanel extends VBox {

    public ConfiguracoesPanel(java.util.function.Consumer<Node> loadPanel,
                               Supplier<Node> makeDashboard,
                               StackPane rootLayer,
                               ConfigService configSvc,
                               BrapiService brapiSvc) {
        setSpacing(0);
        setStyle("-fx-background-color: #0d1117;");

        // Header
        Label titulo = new Label("Configurações");
        titulo.getStyleClass().add("page-title");
        HBox header = new HBox(titulo);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // Card de dados
        Label sectionLabel = new Label("DADOS");
        sectionLabel.getStyleClass().add("section-title");

        Label descricao = new Label(
            "Remove permanentemente todos os investimentos, movimentações, gastos e cotações armazenadas.");
        descricao.setStyle("-fx-text-fill: #7d8fa0; -fx-font-size: 16px;");
        descricao.setWrapText(true);

        Button btnLimpar = new Button("Limpar banco de dados");
        btnLimpar.getStyleClass().add("btn-danger");
        btnLimpar.setOnAction(e -> confirmarELimpar(loadPanel, makeDashboard, rootLayer));

        VBox cardDados = new VBox(12, sectionLabel, descricao, btnLimpar);
        cardDados.getStyleClass().add("card");
        cardDados.setMaxWidth(500);

        // Card BRAPI
        VBox cardBrapi = buildCardBrapi(rootLayer, configSvc, brapiSvc);
        cardBrapi.setMaxWidth(500);

        VBox body = new VBox(24, cardBrapi, cardDados);
        body.setPadding(new Insets(28, 32, 28, 32));
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(header, body);
    }

    private VBox buildCardBrapi(StackPane rootLayer, ConfigService configSvc, BrapiService brapiSvc) {
        Label sectionLabel = new Label("INTEGRAÇÃO BRAPI");
        sectionLabel.getStyleClass().add("section-title");

        Label desc = new Label("Token de autenticação da API brapi.dev para buscar cotações automáticas de ações e FIIs.");
        desc.setStyle("-fx-text-fill: #7d8fa0; -fx-font-size: 16px;");
        desc.setWrapText(true);

        PasswordField fToken = new PasswordField();
        fToken.setPromptText("Cole aqui o token da sua conta brapi.dev");
        fToken.setText(configSvc.getBrapiToken());

        TextField fTokenVisivel = new TextField();
        fTokenVisivel.setPromptText("Cole aqui o token da sua conta brapi.dev");
        fTokenVisivel.setText(configSvc.getBrapiToken());
        fTokenVisivel.setManaged(false);
        fTokenVisivel.setVisible(false);

        fToken.textProperty().bindBidirectional(fTokenVisivel.textProperty());

        Button btnVer = new Button("👁");
        btnVer.setStyle("-fx-padding: 6 10; -fx-font-size: 14px; -fx-cursor: hand;");
        btnVer.setOnAction(e -> {
            boolean mostrar = !fTokenVisivel.isVisible();
            fTokenVisivel.setManaged(mostrar);
            fTokenVisivel.setVisible(mostrar);
            fToken.setManaged(!mostrar);
            fToken.setVisible(!mostrar);
        });

        StackPane tokenStack = new StackPane();
        HBox.setHgrow(tokenStack, Priority.ALWAYS);
        tokenStack.getChildren().addAll(fToken, fTokenVisivel);

        Button btnSalvar = new Button("Salvar");
        btnSalvar.getStyleClass().add("btn-primary");
        btnSalvar.setOnAction(e -> {
            configSvc.setBrapiToken(fToken.getText());
            Toast.show(rootLayer, "Token salvo", "Token BRAPI salvo com sucesso.");
        });

        Button btnTestar = new Button("Testar Conexão");
        btnTestar.getStyleClass().add("btn-secondary");
        btnTestar.setOnAction(e -> {
            String token = fToken.getText().strip();
            btnTestar.setDisable(true);
            btnTestar.setText("Testando...");
            Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; })
                    .submit(() -> {
                        boolean ok = brapiSvc.testarConexao(token);
                        Platform.runLater(() -> {
                            btnTestar.setDisable(false);
                            btnTestar.setText("Testar Conexão");
                            if (ok) {
                                Toast.show(rootLayer, "Conexão OK", "BRAPI respondeu com sucesso.");
                            } else {
                                Toast.show(rootLayer, "Falha na conexão", "Não foi possível conectar à BRAPI. Verifique o token.");
                            }
                        });
                    });
        });

        HBox tokenRow = new HBox(8, tokenStack, btnVer);
        tokenRow.setAlignment(Pos.CENTER_LEFT);

        HBox botoesRow = new HBox(8, btnSalvar, btnTestar);

        VBox card = new VBox(12, sectionLabel, desc, tokenRow, botoesRow);
        card.getStyleClass().add("card");
        return card;
    }

    private void confirmarELimpar(java.util.function.Consumer<Node> loadPanel,
                                   Supplier<Node> makeDashboard,
                                   StackPane rootLayer) {
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType btnLimpar   = new ButtonType("Limpar",   ButtonBar.ButtonData.OK_DONE);

        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
        alerta.setTitle("Confirmar limpeza");
        alerta.setHeaderText("Essa ação não pode ser desfeita.");
        alerta.setContentText(
            "Todos os dados (investimentos, movimentações, gastos, etc.) " +
            "serão apagados permanentemente. Deseja continuar?");
        alerta.getButtonTypes().setAll(btnCancelar, btnLimpar);

        alerta.getDialogPane().lookupButton(btnLimpar)
              .setStyle("-fx-background-color: #f85149; -fx-text-fill: white; -fx-font-weight: bold;");

        Optional<ButtonType> resultado = alerta.showAndWait();
        if (resultado.isPresent() && resultado.get() == btnLimpar) {
            DatabaseManager.getInstance().limparBancoDados();
            loadPanel.accept(makeDashboard.get());
            Toast.show(rootLayer, "Banco limpo", "Todos os dados foram removidos com sucesso.");
        }
    }
}
