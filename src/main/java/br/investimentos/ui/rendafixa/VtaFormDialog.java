package br.investimentos.ui.rendafixa;

import br.investimentos.model.Investimento;
import br.investimentos.model.VtaMensal;
import br.investimentos.repository.*;
import br.investimentos.service.RendimentoService;
import br.investimentos.ui.util.FormatUtil;
import br.investimentos.ui.util.InputUtil;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.time.LocalDate;

public class VtaFormDialog extends Dialog<Void> {

    /** Novo VTA (período padrão = mês atual). */
    public VtaFormDialog(Investimento inv, VtaMensalRepository vtaRepo,
                         VaiAnualRepository vaiRepo, RendimentoService rendSvc) {
        this(inv, vtaRepo, vaiRepo, rendSvc, null);
    }

    /** Editar VTA existente: período e valor pré-preenchidos de {@code preFill}. */
    public VtaFormDialog(Investimento inv, VtaMensalRepository vtaRepo,
                         VaiAnualRepository vaiRepo, RendimentoService rendSvc,
                         VtaMensal preFill) {
        setTitle((preFill != null ? "Editar" : "Informar") + " VTA — " + inv.getNome());
        initModality(Modality.APPLICATION_MODAL);

        LocalDate hoje = LocalDate.now();
        int mesDefault = preFill != null ? preFill.getPeriodoMes() : hoje.getMonthValue();
        int anoDefault = preFill != null ? preFill.getPeriodoAno() : hoje.getYear();

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(20, 24, 20, 24));
        form.setMinWidth(400);

        // Período
        ComboBox<Integer> fMes = new ComboBox<>();
        for (int m = 1; m <= 12; m++) fMes.getItems().add(m);
        fMes.setValue(mesDefault);
        TextField fAno = new TextField();
        fAno.setPrefWidth(80);
        InputUtil.applyIntegerFilter(fAno);
        fAno.setText(String.valueOf(anoDefault));
        HBox periodoBox = new HBox(8, fMes, new Label("/ "), fAno);
        periodoBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        addRow(form, 0, "Período (mês/ano)", periodoBox);

        // VTA
        TextField fVta = new TextField();
        InputUtil.applyDecimalFilter(fVta);
        fVta.setPromptText("Valor total atual do investimento");
        addRow(form, 1, "VTA (R$) *", fVta);

        // VI preview
        Label viLabel = new Label("VI = R$ —");
        viLabel.getStyleClass().add("card-label");
        Label rLabel = new Label("R = R$ —");
        rLabel.getStyleClass().add("card-label");
        form.add(new VBox(4, viLabel, rLabel), 1, 2);
        form.add(new Label(""), 0, 2);

        // Pré-preencher valor
        if (preFill != null) {
            fVta.setText(String.valueOf(preFill.getVta()));
        } else {
            vtaRepo.find(inv.getId(), mesDefault, anoDefault)
                    .ifPresent(v -> fVta.setText(String.valueOf(v.getVta())));
        }

        // Preview ao digitar
        Runnable updatePreview = () -> {
            try {
                int mes = fMes.getValue() != null ? fMes.getValue() : mesDefault;
                int ano = Integer.parseInt(fAno.getText().strip());
                double vi = rendSvc.calcularVi(inv.getId(), ano, mes);
                viLabel.setText("VI = " + FormatUtil.brl(vi));
                if (!fVta.getText().isBlank()) {
                    double vta = Double.parseDouble(fVta.getText().strip().replace(",", "."));
                    double r = vta - vi;
                    rLabel.setText("R = " + FormatUtil.brl(r) + " (" + FormatUtil.pct(vi > 0 ? r / vi * 100 : 0) + ")");
                    rLabel.setStyle(r >= 0 ? "-fx-text-fill: #2e7d32; -fx-font-weight: bold;" : "-fx-text-fill: #c62828; -fx-font-weight: bold;");
                }
            } catch (Exception ex) { /* input incompleto */ }
        };
        fVta.textProperty().addListener((o, a, b) -> updatePreview.run());
        fMes.valueProperty().addListener((o, a, b) -> updatePreview.run());
        fAno.textProperty().addListener((o, a, b) -> updatePreview.run());
        updatePreview.run();

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #c62828;");

        getDialogPane().setContent(new VBox(form, errLabel));
        ButtonType btnSalvar = new ButtonType("Salvar VTA", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(btnSalvar, ButtonType.CANCEL);

        final Button okBtn = (Button) getDialogPane().lookupButton(btnSalvar);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            try {
                int mes = fMes.getValue();
                int ano = Integer.parseInt(fAno.getText().strip());
                double vta = Double.parseDouble(fVta.getText().strip().replace(",", "."));

                VtaMensal vtaM = new VtaMensal();
                vtaM.setInvestimentoId(inv.getId());
                vtaM.setPeriodoMes(mes);
                vtaM.setPeriodoAno(ano);
                vtaM.setVta(vta);
                vtaRepo.salvar(vtaM);
            } catch (NumberFormatException ex) {
                errLabel.setText("Valores inválidos.");
                e.consume();
            }
        });

        setResultConverter(b -> null);
        getDialogPane().setMinWidth(440);
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
