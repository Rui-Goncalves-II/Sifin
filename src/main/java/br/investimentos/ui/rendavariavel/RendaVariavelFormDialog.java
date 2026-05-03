package br.investimentos.ui.rendavariavel;

import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.InvestimentoRepository;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

public class RendaVariavelFormDialog extends Dialog<Void> {

    public RendaVariavelFormDialog(Investimento inv, InvestimentoRepository invRepo) {
        boolean isEdicao = inv != null;
        setTitle(isEdicao ? "Editar Ativo RV — " + inv.getNome() : "Novo Ativo RV");
        initModality(Modality.APPLICATION_MODAL);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(10);
        form.setPadding(new Insets(20, 24, 20, 24));
        form.setMinWidth(400);

        TextField fNome = new TextField();
        fNome.setPromptText("Ex: HGLG11 Carteira");
        addRow(form, 0, "Nome *", fNome);

        ComboBox<String> fSub = new ComboBox<>();
        fSub.getItems().addAll("FII", "ACAO", "ETF");
        fSub.setEditable(true);
        fSub.setPromptText("FII, ACAO, ETF...");
        addRow(form, 1, "Subtipo", fSub);

        TextField fNotas = new TextField();
        fNotas.setPromptText("Ticker ou observações");
        addRow(form, 2, "Notas / Ticker", fNotas);

        if (isEdicao) {
            fNome.setText(inv.getNome());
            if (inv.getSubtipo() != null) fSub.setValue(inv.getSubtipo());
            if (inv.getNotas() != null) fNotas.setText(inv.getNotas());
        }

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #c62828;");
        getDialogPane().setContent(new VBox(8, form, errLabel));

        ButtonType btnSalvar = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(btnSalvar, ButtonType.CANCEL);
        if (isEdicao) {
            ButtonType btnArq = new ButtonType("Arquivar", ButtonBar.ButtonData.LEFT);
            getDialogPane().getButtonTypes().add(0, btnArq);
            ((Button) getDialogPane().lookupButton(btnArq)).setOnAction(e -> {
                Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Arquivar " + inv.getNome() + "?", ButtonType.YES, ButtonType.NO);
                c.showAndWait().filter(r -> r == ButtonType.YES).ifPresent(r -> { invRepo.arquivar(inv.getId()); close(); });
                e.consume();
            });
        }

        ((Button) getDialogPane().lookupButton(btnSalvar)).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            String nome = fNome.getText().strip();
            if (nome.isEmpty()) { errLabel.setText("Nome é obrigatório."); e.consume(); return; }
            Investimento t = isEdicao ? inv : new Investimento();
            t.setNome(nome);
            t.setTipo(TipoInvestimento.RENDA_VARIAVEL);
            t.setSubtipo(fSub.getValue() != null && !fSub.getValue().isBlank() ? fSub.getValue() : null);
            t.setNotas(fNotas.getText().strip().isEmpty() ? null : fNotas.getText().strip());
            invRepo.salvar(t);
        });

        setResultConverter(b -> null);
        getDialogPane().setMinWidth(440);
    }

    private void addRow(GridPane grid, int row, String label, javafx.scene.Node field) {
        Label lbl = new Label(label); lbl.getStyleClass().add("form-label");
        GridPane.setColumnIndex(lbl, 0); GridPane.setRowIndex(lbl, row);
        GridPane.setColumnIndex(field, 1); GridPane.setRowIndex(field, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.getChildren().addAll(lbl, field);
    }
}
