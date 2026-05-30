package br.investimentos.ui.gastos;

import br.investimentos.model.Gasto;
import br.investimentos.model.enums.TipoGasto;
import br.investimentos.repository.GastoRepository;
import br.investimentos.ui.util.InputUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class MensalidadeFormDialog extends Stage {

    private final GastoRepository repo;
    private final Gasto existente;
    private Runnable onSalvo;

    private TextField fDescricao;
    private TextField fMes;
    private TextField fAno;
    private TextField fValor;
    private TextArea fNotas;

    public MensalidadeFormDialog(Window owner, GastoRepository repo, Gasto existente) {
        this.repo = repo;
        this.existente = existente;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(existente == null ? "Nova Mensalidade" : "Editar Mensalidade");
        setResizable(false);

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("dialog-root");
        root.setPrefWidth(420);

        Label title = new Label(existente == null ? "Nova Mensalidade" : "Editar Mensalidade");
        title.getStyleClass().add("dialog-title");

        fDescricao = new TextField();
        InputUtil.applyUpperCaseFilter(fDescricao);
        if (existente != null) fDescricao.setText(existente.getDescricao());
        else fDescricao.setPromptText("Ex: NETFLIX");

        fMes = new TextField();
        InputUtil.applyIntegerFilter(fMes);
        fMes.setPromptText("1–12");
        if (existente != null && existente.getPeriodoMes() != null)
            fMes.setText(String.valueOf(existente.getPeriodoMes()));
        else fMes.setText(String.valueOf(LocalDate.now().getMonthValue()));

        fAno = new TextField();
        InputUtil.applyIntegerFilter(fAno);
        fAno.setPromptText("Ex: 2025");
        if (existente != null && existente.getPeriodoAno() != null)
            fAno.setText(String.valueOf(existente.getPeriodoAno()));
        else fAno.setText(String.valueOf(LocalDate.now().getYear()));

        fValor = new TextField();
        InputUtil.applyDecimalFilter(fValor);
        fValor.setPromptText("Ex: 45,90");
        if (existente != null) fValor.setText(String.valueOf(existente.getValor()).replace(".", ","));

        fNotas = new TextArea();
        fNotas.setPromptText("Observações (opcional)");
        fNotas.setPrefRowCount(2);
        fNotas.setStyle("-fx-font-size: 15px;");
        if (existente != null && existente.getNotas() != null) fNotas.setText(existente.getNotas());

        HBox inicioRow = new HBox(12, campo("Mês de início", fMes), campo("Ano de início", fAno));
        inicioRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(fMes, Priority.ALWAYS);
        HBox.setHgrow(fAno, Priority.ALWAYS);

        Button btnSalvar = new Button("Salvar");
        btnSalvar.getStyleClass().add("btn-primary");
        btnSalvar.setDefaultButton(true);
        btnSalvar.setOnAction(e -> salvar());

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(e -> close());

        HBox btns = new HBox(10, btnSalvar, btnCancelar);
        btns.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
                title,
                campo("Descrição", fDescricao),
                inicioRow,
                campo("Valor mensal (R$)", fValor),
                campo("Notas", fNotas),
                btns
        );

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);
    }

    private VBox campo(String label, javafx.scene.Node field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-label");
        if (field instanceof TextField tf) tf.setStyle("-fx-font-size: 15px;");
        VBox vb = new VBox(4, lbl, field);
        HBox.setHgrow(vb, Priority.ALWAYS);
        return vb;
    }

    private void salvar() {
        String desc = fDescricao.getText().strip();
        if (desc.isBlank()) { alert("Informe a descrição."); return; }

        int mes;
        try {
            mes = Integer.parseInt(fMes.getText().strip());
            if (mes < 1 || mes > 12) throw new NumberFormatException();
        } catch (NumberFormatException ex) { alert("Mês inválido (1–12)."); return; }

        int ano;
        try {
            ano = Integer.parseInt(fAno.getText().strip());
            if (ano < 2000 || ano > 2100) throw new NumberFormatException();
        } catch (NumberFormatException ex) { alert("Ano inválido."); return; }

        BigDecimal valorBD;
        try {
            valorBD = new BigDecimal(InputUtil.normalizeDecimalInput(fValor.getText())).setScale(2, RoundingMode.HALF_UP);
            if (valorBD.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) { alert("Valor inválido."); return; }

        Gasto g = existente != null ? existente : new Gasto();
        g.setTipo(TipoGasto.MENSALIDADE);
        g.setDescricao(desc);
        g.setPeriodoMes(mes);
        g.setPeriodoAno(ano);
        g.setValor(valorBD.doubleValue());
        g.setNotas(fNotas.getText().isBlank() ? null : fNotas.getText().strip());
        repo.salvar(g);

        if (onSalvo != null) onSalvo.run();
        close();
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK).showAndWait();
    }

    public void setOnSalvo(Runnable r) { this.onSalvo = r; }
}
