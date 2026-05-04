package br.investimentos.ui.rendavariavel;

import br.investimentos.model.AporteRv;
import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoOperacaoRv;
import br.investimentos.repository.AporteRvRepository;
import br.investimentos.ui.util.InputUtil;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.time.LocalDate;

public class AporteRvFormDialog extends Dialog<Void> {

    public AporteRvFormDialog(Investimento inv, AporteRvRepository aporteRepo) {
        setTitle("Nova Operação — " + inv.getNome());
        initModality(Modality.APPLICATION_MODAL);

        LocalDate hoje = LocalDate.now();
        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(10);
        form.setPadding(new Insets(20, 24, 20, 24));
        form.setMinWidth(400);

        ComboBox<TipoOperacaoRv> fTipo = new ComboBox<>();
        fTipo.getItems().addAll(TipoOperacaoRv.values());
        fTipo.setValue(TipoOperacaoRv.COMPRA);
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

        TextField fQtd = new TextField();
        InputUtil.applyDecimalFilter(fQtd);
        fQtd.setPromptText("0,0 (deixar vazio para DIVIDENDO)");
        addRow(form, 2, "Quantidade", fQtd);

        TextField fPreco = new TextField();
        InputUtil.applyDecimalFilter(fPreco);
        fPreco.setPromptText("Preço por cota (deixar vazio para DIVIDENDO)");
        addRow(form, 3, "Preço/Cota (R$)", fPreco);

        TextField fValor = new TextField();
        InputUtil.applyDecimalFilter(fValor);
        fValor.setPromptText("Valor total");
        addRow(form, 4, "Valor Total (R$) *", fValor);

        // Auto-calcula valor ao preencher qtd e preço
        Runnable calcValor = () -> {
            try {
                double qtd = Double.parseDouble(fQtd.getText().strip().replace(",", "."));
                double preco = Double.parseDouble(fPreco.getText().strip().replace(",", "."));
                fValor.setText(String.format("%.2f", qtd * preco));
            } catch (NumberFormatException ignored) {}
        };
        fQtd.textProperty().addListener((o, a, b) -> calcValor.run());
        fPreco.textProperty().addListener((o, a, b) -> calcValor.run());

        // Desabilita campos irrelevantes para DIVIDENDO
        fTipo.valueProperty().addListener((o, a, tipo) -> {
            boolean isDividendo = tipo == TipoOperacaoRv.DIVIDENDO;
            fQtd.setDisable(isDividendo);
            fPreco.setDisable(isDividendo);
            if (isDividendo) { fQtd.clear(); fPreco.clear(); }
        });

        TextField fNotas = new TextField(); fNotas.setPromptText("Observações");
        addRow(form, 5, "Notas", fNotas);

        Label errLabel = new Label(); errLabel.setStyle("-fx-text-fill: #c62828;");
        getDialogPane().setContent(new VBox(8, form, errLabel));

        ButtonType btnSalvar = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(btnSalvar, ButtonType.CANCEL);

        ((Button) getDialogPane().lookupButton(btnSalvar)).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            try {
                int mes = fMes.getValue();
                int ano = Integer.parseInt(fAno.getText().strip());
                double valor = Double.parseDouble(fValor.getText().strip().replace(",", "."));
                TipoOperacaoRv tipo = fTipo.getValue();

                AporteRv a = new AporteRv();
                a.setInvestimentoId(inv.getId());
                a.setTipoOp(tipo);
                a.setPeriodoMes(mes); a.setPeriodoAno(ano);
                a.setValor(valor);
                a.setNotas(fNotas.getText().strip().isEmpty() ? null : fNotas.getText().strip());

                if (tipo != TipoOperacaoRv.DIVIDENDO) {
                    if (!fQtd.getText().isBlank()) a.setQuantidade(Double.parseDouble(fQtd.getText().strip().replace(",", ".")));
                    if (!fPreco.getText().isBlank()) a.setPrecoPorCota(Double.parseDouble(fPreco.getText().strip().replace(",", ".")));
                }
                aporteRepo.salvar(a);
            } catch (NumberFormatException ex) {
                errLabel.setText("Valores inválidos."); e.consume();
            }
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
