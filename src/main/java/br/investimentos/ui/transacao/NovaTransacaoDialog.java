package br.investimentos.ui.transacao;

import br.investimentos.model.AporteRv;
import br.investimentos.model.Investimento;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.model.enums.TipoOperacaoRv;
import br.investimentos.repository.AporteRvRepository;
import br.investimentos.repository.InvestimentoRepository;
import br.investimentos.repository.MovimentacaoRepository;
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
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.List;

public class NovaTransacaoDialog extends Stage {

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final AporteRvRepository aporteRepo;
    private Runnable onSalvo;

    private List<Investimento> todosAtivos;
    private Investimento investimentoSelecionado;

    private ComboBox<TipoInvestimento> cbTipoAtivo;
    private TextField fAtivo;
    private ComboBox<String> cbTipoOp;
    private ComboBox<Integer> fDia;
    private ComboBox<Integer> fMes;
    private TextField fAno;
    private TextField fQtd;
    private TextField fPreco;
    private HBox rowRV;
    private TextField fValor;
    private Label lblValor;
    private TextField fCotacao;
    private VBox rowCotacao;
    private TextField fNotas;
    private Label errLabel;

    public NovaTransacaoDialog(Window owner, InvestimentoRepository invRepo,
                                MovimentacaoRepository movRepo, AporteRvRepository aporteRepo) {
        this.invRepo = invRepo;
        this.movRepo = movRepo;
        this.aporteRepo = aporteRepo;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Nova Transação");
        setResizable(false);

        todosAtivos = invRepo.findAll();

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("dialog-root");
        root.setPrefWidth(480);

        Label title = new Label("Nova Transação");
        title.getStyleClass().add("dialog-title");

        // Tipo de ativo
        Label lTipoAtivo = new Label("Tipo de ativo *");
        lTipoAtivo.getStyleClass().add("form-label");
        cbTipoAtivo = new ComboBox<>();
        cbTipoAtivo.getItems().addAll(TipoInvestimento.values());
        cbTipoAtivo.setConverter(new StringConverter<>() {
            @Override public String toString(TipoInvestimento t) { return t == null ? "" : t.label(); }
            @Override public TipoInvestimento fromString(String s) { return null; }
        });
        cbTipoAtivo.setValue(TipoInvestimento.RENDA_FIXA);
        cbTipoAtivo.setMaxWidth(Double.MAX_VALUE);

        // Ativo com busca dinâmica
        Label lAtivo = new Label("Ativo *");
        lAtivo.getStyleClass().add("form-label");
        fAtivo = new TextField();
        fAtivo.setPromptText("Digite para buscar...");
        fAtivo.setMaxWidth(Double.MAX_VALUE);
        InputUtil.applyUpperCaseFilter(fAtivo);

        ContextMenu autocompleteMenu = new ContextMenu();
        boolean[] selecting = {false};
        fAtivo.textProperty().addListener((obs, oldVal, newVal) -> {
            if (selecting[0]) return;
            autocompleteMenu.hide();
            investimentoSelecionado = null;
            if (newVal == null || newVal.isBlank()) return;
            String upper = newVal.toUpperCase();
            TipoInvestimento tipoSel = cbTipoAtivo.getValue();
            List<MenuItem> itens = todosAtivos.stream()
                .filter(i -> tipoSel == null || i.getTipo() == tipoSel)
                .filter(i -> i.getNome().toUpperCase().contains(upper))
                .limit(8)
                .map(i -> {
                    MenuItem mi = new MenuItem(i.getNome());
                    mi.setOnAction(e -> {
                        selecting[0] = true;
                        investimentoSelecionado = i;
                        fAtivo.setText(i.getNome());
                        autocompleteMenu.hide();
                        selecting[0] = false;
                    });
                    return mi;
                })
                .toList();
            if (!itens.isEmpty()) {
                autocompleteMenu.getItems().setAll(itens);
                autocompleteMenu.show(fAtivo, Side.BOTTOM, 0, 0);
            }
        });

        // Tipo de operação
        Label lTipoOp = new Label("Tipo de operação *");
        lTipoOp.getStyleClass().add("form-label");
        cbTipoOp = new ComboBox<>();
        cbTipoOp.setMaxWidth(Double.MAX_VALUE);

        // Período
        Label lPeriodo = new Label("Período (dia/mês/ano) *");
        lPeriodo.getStyleClass().add("form-label");
        LocalDate hoje = LocalDate.now();
        fDia = new ComboBox<>();
        for (int d = 1; d <= 31; d++) fDia.getItems().add(d);
        fDia.setValue(hoje.getDayOfMonth());
        fMes = new ComboBox<>();
        for (int m = 1; m <= 12; m++) fMes.getItems().add(m);
        fMes.setValue(hoje.getMonthValue());
        fAno = new TextField(String.valueOf(hoje.getYear()));
        fAno.setPrefWidth(80);
        InputUtil.applyIntegerFilter(fAno);
        HBox periodoBox = new HBox(8, fDia, new Label("/"), fMes, new Label("/"), fAno);
        periodoBox.setAlignment(Pos.CENTER_LEFT);

        // Campos RV (condicionais)
        fQtd = new TextField();
        InputUtil.applyDecimalFilter(fQtd);
        fQtd.setPromptText("Quantidade");
        Label lQtd = new Label("Quantidade");
        lQtd.getStyleClass().add("form-label");
        VBox colQtd = new VBox(4, lQtd, fQtd);
        HBox.setHgrow(colQtd, Priority.ALWAYS);

        fPreco = new TextField();
        InputUtil.applyDecimalFilter(fPreco);
        fPreco.setPromptText("Preço/Cota (R$)");
        Label lPreco = new Label("Preço/Cota (R$)");
        lPreco.getStyleClass().add("form-label");
        VBox colPreco = new VBox(4, lPreco, fPreco);
        HBox.setHgrow(colPreco, Priority.ALWAYS);

        rowRV = new HBox(12, colQtd, colPreco);

        // Valor
        lblValor = new Label("Valor (R$) *");
        lblValor.getStyleClass().add("form-label");
        fValor = new TextField();
        InputUtil.applyDecimalFilter(fValor);
        fValor.setPromptText("0,00");
        fValor.setMaxWidth(Double.MAX_VALUE);

        // Cotação (condicional para Dólar)
        Label lCotacao = new Label("Cotação R$/USD");
        lCotacao.getStyleClass().add("form-label");
        fCotacao = new TextField();
        InputUtil.applyDecimalFilter(fCotacao);
        fCotacao.setPromptText("Cotação na data");
        fCotacao.setMaxWidth(Double.MAX_VALUE);
        rowCotacao = new VBox(4, lCotacao, fCotacao);

        // Notas
        Label lNotas = new Label("Notas");
        lNotas.getStyleClass().add("form-label");
        fNotas = new TextField();
        fNotas.setPromptText("Opcional");
        fNotas.setMaxWidth(Double.MAX_VALUE);

        errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #f85149;");

        Button btnSalvar = new Button("Salvar");
        btnSalvar.getStyleClass().add("btn-primary");
        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(e -> close());
        HBox buttons = new HBox(12, btnSalvar, btnCancelar);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
            title,
            new VBox(4, lTipoAtivo, cbTipoAtivo),
            new VBox(4, lAtivo, fAtivo),
            new VBox(4, lTipoOp, cbTipoOp),
            new VBox(4, lPeriodo, periodoBox),
            rowRV,
            new VBox(4, lblValor, fValor),
            rowCotacao,
            new VBox(4, lNotas, fNotas),
            errLabel,
            buttons
        );

        cbTipoAtivo.setOnAction(e -> {
            investimentoSelecionado = null;
            selecting[0] = true;
            fAtivo.clear();
            selecting[0] = false;
            autocompleteMenu.hide();
            atualizarPorTipo(cbTipoAtivo.getValue());
        });

        cbTipoOp.setOnAction(e -> atualizarCamposRV());

        Runnable calcValor = () -> {
            try {
                double qtd = InputUtil.parseDoubleField(fQtd.getText());
                double preco = InputUtil.parseDoubleField(fPreco.getText());
                fValor.setText(String.format("%.2f", qtd * preco).replace(".", ","));
            } catch (NumberFormatException ignored) {}
        };
        fQtd.textProperty().addListener((o, a, b) -> calcValor.run());
        fPreco.textProperty().addListener((o, a, b) -> calcValor.run());

        btnSalvar.setOnAction(e -> salvar());

        atualizarPorTipo(TipoInvestimento.RENDA_FIXA);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);
    }

    public void setOnSalvo(Runnable onSalvo) {
        this.onSalvo = onSalvo;
    }

    private void atualizarPorTipo(TipoInvestimento tipo) {
        cbTipoOp.getItems().clear();
        if (tipo == TipoInvestimento.RENDA_VARIAVEL) {
            cbTipoOp.getItems().addAll("Compra", "Venda", "Dividendo");
            cbTipoOp.setValue("Compra");
        } else {
            cbTipoOp.getItems().addAll("Depósito", "Saque");
            cbTipoOp.setValue("Depósito");
        }

        boolean isDolar = tipo == TipoInvestimento.DOLAR;
        lblValor.setText(isDolar ? "Valor (USD) *" : "Valor (R$) *");
        rowCotacao.setVisible(isDolar);
        rowCotacao.setManaged(isDolar);

        atualizarCamposRV();
    }

    private void atualizarCamposRV() {
        boolean isRV = cbTipoAtivo.getValue() == TipoInvestimento.RENDA_VARIAVEL;
        boolean isDividendo = isRV && "Dividendo".equals(cbTipoOp.getValue());
        boolean mostrarRV = isRV && !isDividendo;

        rowRV.setVisible(mostrarRV);
        rowRV.setManaged(mostrarRV);

        if (!mostrarRV) {
            fQtd.clear();
            fPreco.clear();
        }
    }

    private void salvar() {
        errLabel.setText("");

        if (investimentoSelecionado == null) {
            errLabel.setText("Selecione um ativo da lista de sugestões.");
            return;
        }

        int dia;
        int mes;
        int ano;
        try {
            dia = fDia.getValue();
            mes = fMes.getValue();
            ano = Integer.parseInt(fAno.getText().strip());
        } catch (Exception ex) {
            errLabel.setText("Período inválido.");
            return;
        }

        double valor;
        try {
            valor = InputUtil.parseDoubleField(fValor.getText());
            if (valor <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            errLabel.setText("Valor deve ser positivo.");
            return;
        }

        String notas = fNotas.getText().strip().isEmpty() ? null : fNotas.getText().strip();
        TipoInvestimento tipo = cbTipoAtivo.getValue();

        try {
            if (tipo == TipoInvestimento.RENDA_VARIAVEL) {
                AporteRv aporte = new AporteRv();
                aporte.setInvestimentoId(investimentoSelecionado.getId());
                aporte.setTipoOp(mapTipoOp(cbTipoOp.getValue()));
                aporte.setPeriodoDia(dia);
                aporte.setPeriodoMes(mes);
                aporte.setPeriodoAno(ano);
                aporte.setValor(valor);
                aporte.setNotas(notas);
                if (!"Dividendo".equals(cbTipoOp.getValue())) {
                    if (!fQtd.getText().isBlank())
                        aporte.setQuantidade(InputUtil.parseDoubleField(fQtd.getText()));
                    if (!fPreco.getText().isBlank())
                        aporte.setPrecoPorCota(InputUtil.parseDoubleField(fPreco.getText()));
                }
                aporteRepo.salvar(aporte);
            } else {
                Movimentacao mov = new Movimentacao();
                mov.setInvestimentoId(investimentoSelecionado.getId());
                mov.setTipoMov("Depósito".equals(cbTipoOp.getValue()) ? TipoMovimentacao.DEPOSITO : TipoMovimentacao.SAQUE);
                mov.setPeriodoDia(dia);
                mov.setPeriodoMes(mes);
                mov.setPeriodoAno(ano);
                mov.setValor(valor);
                mov.setNotas(notas);
                if (tipo == TipoInvestimento.DOLAR && !fCotacao.getText().isBlank())
                    mov.setCotacaoDolar(InputUtil.parseDoubleField(fCotacao.getText()));
                movRepo.salvar(mov);
            }
        } catch (NumberFormatException ex) {
            errLabel.setText("Valores inválidos.");
            return;
        }

        if (onSalvo != null) onSalvo.run();
        close();
    }

    private TipoOperacaoRv mapTipoOp(String label) {
        return switch (label) {
            case "Venda" -> TipoOperacaoRv.VENDA;
            case "Dividendo" -> TipoOperacaoRv.DIVIDENDO;
            default -> TipoOperacaoRv.COMPRA;
        };
    }
}
