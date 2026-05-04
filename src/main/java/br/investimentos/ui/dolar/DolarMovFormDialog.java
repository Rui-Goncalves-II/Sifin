package br.investimentos.ui.dolar;

import br.investimentos.model.CotacaoDolar;
import br.investimentos.model.Investimento;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.repository.MovimentacaoRepository;
import br.investimentos.ui.util.InputUtil;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.time.LocalDate;

public class DolarMovFormDialog extends Dialog<Void> {

    public DolarMovFormDialog(Investimento inv, MovimentacaoRepository movRepo) {
        setTitle("Movimentação USD — " + inv.getNome());
        initModality(Modality.APPLICATION_MODAL);

        LocalDate hoje = LocalDate.now();
        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(10);
        form.setPadding(new Insets(20, 24, 20, 24));
        form.setMinWidth(380);

        ComboBox<TipoMovimentacao> fTipo = new ComboBox<>();
        fTipo.getItems().addAll(TipoMovimentacao.values());
        fTipo.setValue(TipoMovimentacao.DEPOSITO);
        addRow(form, 0, "Tipo *", fTipo);

        ComboBox<Integer> fMes = new ComboBox<>();
        for (int m = 1; m <= 12; m++) fMes.getItems().add(m);
        fMes.setValue(hoje.getMonthValue());
        TextField fAno = new TextField(); fAno.setPrefWidth(70);
        InputUtil.applyIntegerFilter(fAno);
        fAno.setText(String.valueOf(hoje.getYear()));
        HBox perBox = new HBox(8, fMes, new Label("/ "), fAno);
        perBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        addRow(form, 1, "Período", perBox);

        TextField fValor = new TextField();
        InputUtil.applyDecimalFilter(fValor);
        fValor.setPromptText("Valor em USD");
        addRow(form, 2, "Valor (USD) *", fValor);

        TextField fCot = new TextField();
        InputUtil.applyDecimalFilter(fCot);
        fCot.setPromptText("Cotação R$/USD na data");
        addRow(form, 3, "Cotação R$/USD", fCot);

        TextField fNotas = new TextField(); fNotas.setPromptText("Observações");
        addRow(form, 4, "Notas", fNotas);

        Label errLabel = new Label(); errLabel.setStyle("-fx-text-fill: #c62828;");
        getDialogPane().setContent(new VBox(8, form, errLabel));

        ButtonType btnSalvar = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(btnSalvar, ButtonType.CANCEL);

        ((Button) getDialogPane().lookupButton(btnSalvar)).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            try {
                int mes = fMes.getValue();
                int ano = Integer.parseInt(fAno.getText().strip());
                double valor = Double.parseDouble(fValor.getText().strip().replace(",", "."));

                Movimentacao m = new Movimentacao();
                m.setInvestimentoId(inv.getId());
                m.setTipoMov(fTipo.getValue());
                m.setPeriodoMes(mes); m.setPeriodoAno(ano);
                m.setValor(valor);
                if (!fCot.getText().isBlank())
                    m.setCotacaoDolar(Double.parseDouble(fCot.getText().strip().replace(",", ".")));
                m.setNotas(fNotas.getText().strip().isEmpty() ? null : fNotas.getText().strip());
                movRepo.salvar(m);
            } catch (NumberFormatException ex) {
                errLabel.setText("Valores inválidos."); e.consume();
            }
        });

        setResultConverter(b -> null);
        getDialogPane().setMinWidth(420);
    }

    private void addRow(GridPane grid, int row, String label, javafx.scene.Node field) {
        Label lbl = new Label(label); lbl.getStyleClass().add("form-label");
        GridPane.setColumnIndex(lbl, 0); GridPane.setRowIndex(lbl, row);
        GridPane.setColumnIndex(field, 1); GridPane.setRowIndex(field, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.getChildren().addAll(lbl, field);
    }
}
