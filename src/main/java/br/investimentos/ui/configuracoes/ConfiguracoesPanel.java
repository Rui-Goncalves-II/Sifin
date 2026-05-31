package br.investimentos.ui.configuracoes;

import br.investimentos.db.DatabaseManager;
import br.investimentos.ui.util.Toast;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Optional;
import java.util.function.Supplier;

public class ConfiguracoesPanel extends VBox {

    public ConfiguracoesPanel(java.util.function.Consumer<Node> loadPanel,
                               Supplier<Node> makeDashboard,
                               StackPane rootLayer) {
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

        VBox card = new VBox(12, sectionLabel, descricao, btnLimpar);
        card.getStyleClass().add("card");
        card.setMaxWidth(500);

        VBox body = new VBox(24, card);
        body.setPadding(new Insets(28, 32, 28, 32));
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(header, body);
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

        // Estiliza o botão "Limpar" em vermelho
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
