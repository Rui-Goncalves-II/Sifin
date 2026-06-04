package br.investimentos.ui.gastos;

import br.investimentos.model.Gasto;
import br.investimentos.model.enums.TipoGasto;
import br.investimentos.repository.GastoRepository;
import br.investimentos.ui.util.InputUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GastoFormDialog extends Stage {

    private static final boolean SUPORTA_PARCELAMENTO =
            true; // controlado externamente via tipo — ver constructor

    private final GastoRepository repo;
    private final TipoGasto tipo;
    private final Gasto existente;
    private final boolean podeParcelar;
    private Runnable onSalvo;

    private TextField fDescricao;
    private TextField fMes;
    private TextField fAno;
    private TextField fValor;
    private TextArea fNotas;
    private CheckBox cbParcelado;
    private TextField fParcelas;
    private Label lblValor;
    private VBox rowParcelamento;

    public GastoFormDialog(Window owner, GastoRepository repo, TipoGasto tipo, Gasto existente) {
        this.repo = repo;
        this.tipo = tipo;
        this.existente = existente;
        this.podeParcelar = (tipo == TipoGasto.ALIMENTAR || tipo == TipoGasto.DIVERSO)
                && existente == null; // parcelamento só na criação

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle(existente == null
                ? "Novo gasto — " + tipo.getLabel()
                : "Editar gasto — " + tipo.getLabel());
        setResizable(false);

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("dialog-root");
        root.setPrefWidth(440);

        Label title = new Label(existente == null ? "Novo Gasto" : "Editar Gasto");
        title.getStyleClass().add("dialog-title");

        fDescricao = new TextField();
        InputUtil.applyUpperCaseFilter(fDescricao);
        if (existente != null) fDescricao.setText(existente.getDescricao());
        else fDescricao.setPromptText("Ex: SUPERMERCADO EXTRA");

        List<String> descricoesCadastradas = repo.findByTipo(tipo).stream()
                .map(Gasto::getDescricao)
                .distinct()
                .sorted()
                .toList();
        ContextMenu autocompleteMenu = new ContextMenu();
        fDescricao.textProperty().addListener((obs, oldVal, newVal) -> {
            autocompleteMenu.hide();
            if (newVal == null || newVal.isBlank()) return;
            String upper = newVal.toUpperCase();
            List<MenuItem> itens = descricoesCadastradas.stream()
                    .filter(d -> d.contains(upper))
                    .limit(8)
                    .map(d -> {
                        MenuItem mi = new MenuItem(d);
                        mi.setOnAction(e -> fDescricao.setText(d));
                        return mi;
                    })
                    .toList();
            if (!itens.isEmpty()) {
                autocompleteMenu.getItems().setAll(itens);
                autocompleteMenu.show(fDescricao, Side.BOTTOM, 0, 0);
            }
        });

        fMes = new TextField();
        InputUtil.applyIntegerFilter(fMes);
        fMes.setPromptText("1–12");
        if (existente != null) fMes.setText(String.valueOf(existente.getPeriodoMes()));
        else fMes.setText(String.valueOf(LocalDate.now().getMonthValue()));

        fAno = new TextField();
        InputUtil.applyIntegerFilter(fAno);
        fAno.setPromptText("Ex: 2025");
        if (existente != null) fAno.setText(String.valueOf(existente.getPeriodoAno()));
        else fAno.setText(String.valueOf(LocalDate.now().getYear()));

        lblValor = new Label("Valor (R$)");
        lblValor.getStyleClass().add("card-label");

        fValor = new TextField();
        InputUtil.applyDecimalFilter(fValor);
        fValor.setPromptText("Ex: 350,00");
        if (existente != null) fValor.setText(String.valueOf(existente.getValor()).replace(".", ","));

        fNotas = new TextArea();
        fNotas.setPromptText("Observações (opcional)");
        fNotas.setPrefRowCount(2);
        fNotas.setStyle("-fx-font-size: 15px;");
        if (existente != null && existente.getNotas() != null) fNotas.setText(existente.getNotas());

        HBox periodoRow = new HBox(12, campo("Mês", fMes), campo("Ano", fAno));
        periodoRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(fMes, Priority.ALWAYS);
        HBox.setHgrow(fAno, Priority.ALWAYS);

        VBox valorBox = new VBox(4, lblValor, fValor);
        HBox.setHgrow(valorBox, Priority.ALWAYS);
        fValor.setStyle("-fx-font-size: 15px;");

        rowParcelamento = buildRowParcelamento();

        Button btnSalvar = new Button("Salvar");
        btnSalvar.getStyleClass().add("btn-primary");
        btnSalvar.setDefaultButton(true);
        btnSalvar.setOnAction(e -> salvar());

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(e -> close());

        HBox btns = new HBox(10, btnSalvar, btnCancelar);
        btns.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, campo("Descrição", fDescricao), periodoRow,
                valorBox, rowParcelamento, campo("Notas", fNotas), btns);

        // Mostra info de parcelamento para gastos parcelados existentes
        if (existente != null && existente.isParcelado()) {
            Label infoParc = new Label(
                    "Parcela " + existente.getParcelaNumero() + " de " + existente.getParcelasTotal());
            infoParc.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 13px;");
            root.getChildren().add(root.getChildren().size() - 1, infoParc);
        }

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);
    }

    private VBox buildRowParcelamento() {
        cbParcelado = new CheckBox("Compra parcelada");
        cbParcelado.setStyle("-fx-font-size: 15px;");

        fParcelas = new TextField();
        InputUtil.applyIntegerFilter(fParcelas);
        fParcelas.setPromptText("2–48");
        fParcelas.setPrefWidth(80);
        fParcelas.setStyle("-fx-font-size: 15px;");

        Label lblParcelas = new Label("Nº de parcelas:");
        lblParcelas.setStyle("-fx-font-size: 15px; -fx-text-fill: #8b949e;");

        HBox parcelasRow = new HBox(8, lblParcelas, fParcelas);
        parcelasRow.setAlignment(Pos.CENTER_LEFT);
        parcelasRow.setVisible(false);
        parcelasRow.setManaged(false);

        cbParcelado.selectedProperty().addListener((obs, wasOn, isOn) -> {
            parcelasRow.setVisible(isOn);
            parcelasRow.setManaged(isOn);
            if (isOn) {
                lblValor.setText("Valor total (R$)");
                if (fParcelas.getText().isBlank()) fParcelas.setText("2");
            } else {
                lblValor.setText("Valor (R$)");
            }
        });

        VBox box = new VBox(8, cbParcelado, parcelasRow);

        if (!podeParcelar) {
            box.setVisible(false);
            box.setManaged(false);
        }

        return box;
    }

    private VBox campo(String label, javafx.scene.Node field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-label");
        if (field instanceof TextField tf) tf.setStyle("-fx-font-size: 15px;");
        VBox vb = new VBox(4, lbl, field);
        VBox.setVgrow(field, Priority.ALWAYS);
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

        boolean parcelado = podeParcelar && cbParcelado.isSelected();
        int nParcelas = 1;
        if (parcelado) {
            try {
                nParcelas = Integer.parseInt(fParcelas.getText().strip());
                if (nParcelas < 2 || nParcelas > 48) throw new NumberFormatException();
            } catch (NumberFormatException ex) { alert("Número de parcelas inválido (2–48)."); return; }
        }

        if (parcelado) {
            salvarParcelado(desc, mes, ano, valorBD, nParcelas);
        } else {
            salvarSimples(desc, mes, ano, valorBD.doubleValue());
        }

        if (onSalvo != null) onSalvo.run();
        close();
    }

    private void salvarSimples(String desc, int mes, int ano, double valor) {
        Gasto g = existente != null ? existente : new Gasto();
        g.setTipo(tipo);
        g.setDescricao(desc);
        g.setPeriodoMes(mes);
        g.setPeriodoAno(ano);
        g.setValor(valor);
        g.setNotas(fNotas.getText().isBlank() ? null : fNotas.getText().strip());
        repo.salvar(g);
    }

    private void salvarParcelado(String desc, int mesPrimeiro, int anoPrimeiro,
                                  BigDecimal total, int nParcelas) {
        BigDecimal n = BigDecimal.valueOf(nParcelas);
        BigDecimal valorParcela = total.divide(n, 2, RoundingMode.HALF_UP);
        // diferença de centavos vai para a primeira parcela
        BigDecimal valorPrimeira = total.subtract(valorParcela.multiply(BigDecimal.valueOf(nParcelas - 1)));

        String grupo = UUID.randomUUID().toString();
        String notas = fNotas.getText().isBlank() ? null : fNotas.getText().strip();

        List<Gasto> parcelas = new ArrayList<>();
        for (int i = 0; i < nParcelas; i++) {
            LocalDate d = LocalDate.of(anoPrimeiro, mesPrimeiro, 1).plusMonths(i);
            Gasto g = new Gasto();
            g.setTipo(tipo);
            g.setDescricao(desc);
            g.setPeriodoMes(d.getMonthValue());
            g.setPeriodoAno(d.getYear());
            g.setValor((i == 0 ? valorPrimeira : valorParcela).doubleValue());
            g.setNotas(notas);
            g.setParcelasTotal(nParcelas);
            g.setParcelaNumero(i + 1);
            g.setGrupoParcela(grupo);
            parcelas.add(g);
        }
        parcelas.forEach(repo::salvar);
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK).showAndWait();
    }

    public void setOnSalvo(Runnable r) { this.onSalvo = r; }
}
