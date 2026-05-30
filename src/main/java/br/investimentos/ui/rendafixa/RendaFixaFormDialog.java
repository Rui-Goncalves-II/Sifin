package br.investimentos.ui.rendafixa;

import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.InvestimentoRepository;
import br.investimentos.ui.util.InputUtil;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

public class RendaFixaFormDialog extends Dialog<Void> {

    private final InvestimentoRepository invRepo;

    public RendaFixaFormDialog(Investimento inv, InvestimentoRepository invRepo) {
        this.invRepo = invRepo;
        boolean isEdicao = inv != null;

        setTitle(isEdicao ? "Editar Ativo RF" : "Novo Ativo RF");
        initModality(Modality.APPLICATION_MODAL);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(20, 24, 20, 24));
        form.setMinWidth(420);

        // Nome
        TextField fNome = new TextField();
        InputUtil.applyUpperCaseFilter(fNome);
        fNome.setPromptText("Ex: PORQUINHO CDB");
        addRow(form, 0, "Nome *", fNome);

        // Subtipo
        ComboBox<String> fSub = new ComboBox<>();
        fSub.getItems().addAll("CDB", "LCI", "LCA", "TESOURO", "LC", "CRI", "CRA");
        fSub.setEditable(true);
        fSub.setPromptText("CDB, LCI, LCA...");
        addRow(form, 1, "Subtipo", fSub);

        // Indexador
        ComboBox<String> fIdx = new ComboBox<>();
        fIdx.getItems().addAll("CDI", "IPCA", "SELIC", "PREFIXADO");
        fIdx.setEditable(true);
        fIdx.setPromptText("CDI, IPCA, SELIC...");
        addRow(form, 2, "Indexador", fIdx);

        // Taxa anual
        TextField fTaxa = new TextField();
        InputUtil.applyDecimalFilter(fTaxa);
        fTaxa.setPromptText("Ex: 12,5 (opcional — usa cálculo se vazio)");
        addRow(form, 3, "Taxa Anual %", fTaxa);

        // Vencimento
        TextField fVenc = new TextField();
        fVenc.setPromptText("YYYY-MM-DD");
        addRow(form, 4, "Vencimento", fVenc);

        // Notas
        TextArea fNotas = new TextArea();
        fNotas.setPromptText("Observações...");
        fNotas.setPrefRowCount(3);
        addRow(form, 5, "Notas", fNotas);

        if (isEdicao) {
            fNome.setText(inv.getNome());
            if (inv.getSubtipo() != null) fSub.setValue(inv.getSubtipo());
            if (inv.getIndexador() != null) fIdx.setValue(inv.getIndexador());
            if (inv.getTaxaAnual() != null) fTaxa.setText(String.valueOf(inv.getTaxaAnual()).replace(".", ","));
            if (inv.getDataVencimento() != null) fVenc.setText(inv.getDataVencimento());
            if (inv.getNotas() != null) fNotas.setText(inv.getNotas());
        }

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #c62828;");

        VBox content = new VBox(form, errLabel);
        content.setPadding(Insets.EMPTY);
        getDialogPane().setContent(content);

        ButtonType btnSalvar = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        if (isEdicao) {
            ButtonType btnArq = new ButtonType("Arquivar", ButtonBar.ButtonData.LEFT);
            getDialogPane().getButtonTypes().addAll(btnSalvar, btnArq, btnCancel);
            final Button archiveBtn = (Button) getDialogPane().lookupButton(btnArq);
            archiveBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Arquivar " + inv.getNome() + "?", ButtonType.YES, ButtonType.NO);
                confirm.showAndWait().filter(r -> r == ButtonType.YES).ifPresent(r -> {
                    invRepo.arquivar(inv.getId());
                    close();
                });
                e.consume();
            });
        } else {
            getDialogPane().getButtonTypes().addAll(btnSalvar, btnCancel);
        }

        final Button okBtn = (Button) getDialogPane().lookupButton(btnSalvar);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            String nome = fNome.getText().strip();
            if (nome.isEmpty()) {
                errLabel.setText("Nome é obrigatório.");
                e.consume();
                return;
            }

            Investimento toSave = isEdicao ? inv : new Investimento();
            toSave.setNome(nome);
            toSave.setTipo(TipoInvestimento.RENDA_FIXA);
            toSave.setSubtipo(fSub.getValue() != null && !fSub.getValue().isBlank() ? fSub.getValue() : null);
            toSave.setIndexador(fIdx.getValue() != null && !fIdx.getValue().isBlank() ? fIdx.getValue() : null);
            String taxaTxt = InputUtil.normalizeDecimalInput(fTaxa.getText());
            if (!taxaTxt.isEmpty()) {
                try { toSave.setTaxaAnual(Double.parseDouble(taxaTxt)); }
                catch (NumberFormatException ex) { errLabel.setText("Taxa inválida."); e.consume(); return; }
            } else { toSave.setTaxaAnual(null); }
            toSave.setDataVencimento(fVenc.getText().strip().isEmpty() ? null : fVenc.getText().strip());
            toSave.setNotas(fNotas.getText().strip().isEmpty() ? null : fNotas.getText().strip());
            invRepo.salvar(toSave);
        });

        setResultConverter(btn -> null);
        getDialogPane().setMinWidth(460);
    }

    private void addRow(GridPane grid, int row, String label, javafx.scene.Node field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        GridPane.setColumnIndex(lbl, 0);
        GridPane.setRowIndex(lbl, row);
        GridPane.setColumnIndex(field, 1);
        GridPane.setRowIndex(field, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.getChildren().addAll(lbl, field);
    }
}
