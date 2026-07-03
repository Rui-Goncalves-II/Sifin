package br.investimentos.ui.rendafixa;

import br.investimentos.model.Investimento;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.repository.MovimentacaoRepository;
import br.investimentos.ui.util.InputUtil;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.time.LocalDate;

public class MovimentacaoFormDialog extends Dialog<Boolean> {

    public MovimentacaoFormDialog(Investimento inv, Movimentacao mov,
                                   MovimentacaoRepository movRepo) {
        boolean isEdit = mov.getId() != 0;
        setTitle((isEdit ? "Editar" : "Nova") + " Movimentação — " + inv.getNome());
        initModality(Modality.APPLICATION_MODAL);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(20, 24, 20, 24));
        form.setMinWidth(400);

        LocalDate hoje = LocalDate.now();

        ComboBox<TipoMovimentacao> fTipo = new ComboBox<>();
        fTipo.getItems().addAll(TipoMovimentacao.DEPOSITO, TipoMovimentacao.SAQUE);
        fTipo.setValue(mov.getTipoMov() != null ? mov.getTipoMov() : TipoMovimentacao.DEPOSITO);
        addRow(form, 0, "Tipo *", fTipo);

        ComboBox<Integer> fDia = new ComboBox<>();
        for (int d = 1; d <= 31; d++) fDia.getItems().add(d);
        fDia.setValue(mov.getPeriodoDia() != 0 ? mov.getPeriodoDia() : hoje.getDayOfMonth());
        ComboBox<Integer> fMes = new ComboBox<>();
        for (int m = 1; m <= 12; m++) fMes.getItems().add(m);
        fMes.setValue(mov.getPeriodoMes() != 0 ? mov.getPeriodoMes() : hoje.getMonthValue());
        TextField fAno = new TextField();
        fAno.setPrefWidth(80);
        InputUtil.applyIntegerFilter(fAno);
        fAno.setText(mov.getPeriodoAno() != 0 ? String.valueOf(mov.getPeriodoAno()) : String.valueOf(hoje.getYear()));
        HBox periodoBox = new HBox(8, fDia, new Label("/"), fMes, new Label("/ "), fAno);
        periodoBox.setAlignment(Pos.CENTER_LEFT);
        addRow(form, 1, "Período (dia/mês/ano) *", periodoBox);

        TextField fValor = new TextField();
        InputUtil.applyDecimalFilter(fValor);
        fValor.setPromptText("Sempre positivo");
        if (mov.getValor() != 0) fValor.setText(String.valueOf(mov.getValor()));
        addRow(form, 2, "Valor (R$) *", fValor);

        TextField fNotas = new TextField(mov.getNotas() != null ? mov.getNotas() : "");
        fNotas.setPromptText("Opcional");
        addRow(form, 3, "Notas", fNotas);

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #f85149;");

        getDialogPane().setContent(new VBox(form, errLabel));
        ButtonType btnSalvar = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(btnSalvar, ButtonType.CANCEL);
        getDialogPane().setMinWidth(460);

        Button okBtn = (Button) getDialogPane().lookupButton(btnSalvar);
        okBtn.addEventFilter(ActionEvent.ACTION, e -> {
            try {
                int dia = fDia.getValue();
                int mes = fMes.getValue();
                int ano = Integer.parseInt(fAno.getText().strip());
                double valor = InputUtil.parseDoubleField(fValor.getText());
                if (valor <= 0) {
                    errLabel.setText("Valor deve ser positivo.");
                    e.consume();
                    return;
                }
                mov.setInvestimentoId(inv.getId());
                mov.setTipoMov(fTipo.getValue());
                mov.setPeriodoDia(dia);
                mov.setPeriodoMes(mes);
                mov.setPeriodoAno(ano);
                mov.setValor(valor);
                mov.setNotas(fNotas.getText().strip());
                movRepo.salvar(mov);
            } catch (NumberFormatException ex) {
                errLabel.setText("Valores inválidos.");
                e.consume();
            }
        });

        setResultConverter(b -> b != null && b.getButtonData() == ButtonBar.ButtonData.OK_DONE);
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
