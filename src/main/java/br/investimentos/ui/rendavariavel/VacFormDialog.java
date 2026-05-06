package br.investimentos.ui.rendavariavel;

import br.investimentos.model.Investimento;
import br.investimentos.model.VacMensal;
import br.investimentos.repository.VacMensalRepository;
import br.investimentos.ui.util.InputUtil;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.time.LocalDate;

public class VacFormDialog extends Dialog<Void> {

    public VacFormDialog(Investimento inv, VacMensalRepository vacRepo) {
        setTitle("Atualizar VAC — " + inv.getNome());
        initModality(Modality.APPLICATION_MODAL);

        LocalDate hoje = LocalDate.now();
        int mesAtual = hoje.getMonthValue();
        int anoAtual = hoje.getYear();

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(10);
        form.setPadding(new Insets(20, 24, 20, 24));
        form.setMinWidth(380);

        ComboBox<Integer> fMes = new ComboBox<>();
        for (int m = 1; m <= 12; m++) fMes.getItems().add(m);
        fMes.setValue(mesAtual);
        TextField fAno = new TextField();
        fAno.setPrefWidth(80);
        InputUtil.applyIntegerFilter(fAno);
        fAno.setText(String.valueOf(anoAtual));
        HBox periodoBox = new HBox(8, fMes, new Label("/ "), fAno);
        periodoBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        addRow(form, 0, "Período", periodoBox);

        TextField fVac = new TextField();
        InputUtil.applyDecimalFilter(fVac);
        fVac.setPromptText("Ex: 12,50");
        addRow(form, 1, "VAC (R$) *", fVac);

        vacRepo.find(inv.getId(), mesAtual, anoAtual).ifPresent(v ->
                fVac.setText(String.valueOf(v.getVac()).replace(".", ",")));

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #c62828;");
        getDialogPane().setContent(new VBox(8, form, errLabel));

        ButtonType btnSalvar = new ButtonType("Salvar VAC", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(btnSalvar, ButtonType.CANCEL);

        ((Button) getDialogPane().lookupButton(btnSalvar)).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            try {
                int mes = fMes.getValue();
                int ano = Integer.parseInt(fAno.getText().strip());
                double vac = Double.parseDouble(fVac.getText().strip().replace(",", "."));
                VacMensal v = new VacMensal();
                v.setInvestimentoId(inv.getId());
                v.setPeriodoMes(mes);
                v.setPeriodoAno(ano);
                v.setVac(vac);
                v.setFonte("MANUAL");
                vacRepo.salvar(v);
            } catch (NumberFormatException ex) {
                errLabel.setText("Valor inválido.");
                e.consume();
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
