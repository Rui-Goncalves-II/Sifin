package br.investimentos.ui.transacao;

import br.investimentos.model.AporteRv;
import br.investimentos.model.Investimento;
import br.investimentos.model.Movimentacao;
import br.investimentos.db.DatabaseManager;
import br.investimentos.repository.AporteRvRepository;
import br.investimentos.repository.InvestimentoRepository;
import br.investimentos.repository.MovimentacaoRepository;
import br.investimentos.service.ImportExportService;
import br.investimentos.ui.util.FormatUtil;
import br.investimentos.ui.util.InputUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class TransacaoPanel extends BorderPane {

    private static final String[] MESES_NOME = {
        "Todos", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    };

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final AporteRvRepository aporteRepo;
    private final ImportExportService importExportSvc;

    private List<LinhaTransacao> todasLinhas = new ArrayList<>();
    private TableView<LinhaTransacao> table;

    private TextField fNome;
    private ComboBox<String> cbTipoAtivo;
    private ComboBox<String> cbTipoTransacao;
    private ComboBox<String> cbMes;
    private ComboBox<String> cbAno;
    private TextField fValorMin;
    private TextField fValorMax;

    record LinhaTransacao(int periodoDia, int periodoMes, int periodoAno, String ativo, String tipoAtivo, String tipo, double valor, String notas) {}

    public TransacaoPanel(InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
                          AporteRvRepository aporteRepo, ImportExportService importExportSvc) {
        this.invRepo = invRepo;
        this.movRepo = movRepo;
        this.aporteRepo = aporteRepo;
        this.importExportSvc = importExportSvc;
        construir();
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("🔄 Transações");
        title.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnExportar = new Button("⬇ Exportar CSV");
        btnExportar.getStyleClass().add("btn-secondary");
        btnExportar.setOnAction(e -> exportarCSV());

        Button btnRestaurar = new Button("↺ Restaurar backup");
        btnRestaurar.getStyleClass().add("btn-warning");
        btnRestaurar.setOnAction(e -> restaurarBackup());

        Button btnImportar = new Button("⬆ Importar CSV");
        btnImportar.getStyleClass().add("btn-primary");
        btnImportar.setOnAction(e -> importarCSV());

        header.getChildren().addAll(title, spacer, btnExportar, btnRestaurar, btnImportar);
        setTop(header);

        carregarDados();
        table = buildTable();

        VBox content = new VBox(12);
        content.setPadding(new Insets(20, 24, 24, 24));
        content.getChildren().addAll(buildFiltros(), table);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);

        aplicarFiltros();
    }

    private void exportarCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Salvar backup CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivo CSV", "*.csv"));
        String hoje = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        fc.setInitialFileName("sifin_backup_" + hoje + ".csv");
        File arquivo = fc.showSaveDialog(getScene().getWindow());
        if (arquivo == null) return;
        try {
            importExportSvc.exportar(arquivo);
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Exportação concluída");
            ok.setHeaderText(null);
            ok.setContentText("Dados exportados com sucesso para:\n" + arquivo.getAbsolutePath());
            ok.showAndWait();
        } catch (IOException ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Erro ao exportar");
            err.setHeaderText(null);
            err.setContentText("Falha ao exportar: " + ex.getMessage());
            err.showAndWait();
        }
    }

    private void importarCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Selecionar backup CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivo CSV", "*.csv"));
        File arquivo = fc.showOpenDialog(getScene().getWindow());
        if (arquivo == null) return;

        Alert aviso = new Alert(Alert.AlertType.CONFIRMATION);
        aviso.setTitle("Importar dados");
        aviso.setHeaderText("Atenção");
        aviso.setContentText(
            "Os dados do arquivo serão adicionados ao banco atual.\n" +
            "Se já existirem registros idênticos, podem ser criados duplicados.\n\n" +
            "Deseja continuar?");
        if (aviso.showAndWait().filter(r -> r == ButtonType.OK).isEmpty()) return;

        try {
            ImportExportService.ImportResult res = importExportSvc.importar(arquivo);
            carregarDados();
            recarregarFiltroAno();
            aplicarFiltros();
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Importação concluída");
            ok.setHeaderText(null);
            ok.setContentText(res.toMessage());
            ok.showAndWait();
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Erro ao importar");
            err.setHeaderText(null);
            err.setContentText("Falha ao importar: " + ex.getMessage());
            err.showAndWait();
        }
    }

    private void restaurarBackup() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Selecionar backup CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivo CSV", "*.csv"));
        File arquivo = fc.showOpenDialog(getScene().getWindow());
        if (arquivo == null) return;

        Alert aviso = new Alert(Alert.AlertType.CONFIRMATION);
        aviso.setTitle("Restaurar backup");
        aviso.setHeaderText("Todos os dados atuais serão apagados.");
        aviso.setContentText(
            "Esta operação apaga TODOS os investimentos, movimentações e gastos existentes " +
            "e os substitui pelos dados do arquivo selecionado.\n\n" +
            "Use esta opção apenas para restaurar um backup completo.\n\n" +
            "Deseja continuar?");
        if (aviso.showAndWait().filter(r -> r == ButtonType.OK).isEmpty()) return;

        try {
            DatabaseManager.getInstance().limparBancoDados();
            ImportExportService.ImportResult res = importExportSvc.importar(arquivo);
            carregarDados();
            recarregarFiltroAno();
            aplicarFiltros();
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Restauração concluída");
            ok.setHeaderText(null);
            ok.setContentText(res.toMessage());
            ok.showAndWait();
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Erro ao restaurar");
            err.setHeaderText(null);
            err.setContentText("Falha ao restaurar backup: " + ex.getMessage());
            err.showAndWait();
        }
    }

    private void recarregarFiltroAno() {
        String anoAtual = cbAno.getValue();
        cbAno.getItems().clear();
        cbAno.getItems().add("Todos");
        todasLinhas.stream()
            .map(LinhaTransacao::periodoAno)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .map(String::valueOf)
            .forEach(cbAno.getItems()::add);
        cbAno.setValue(cbAno.getItems().contains(anoAtual) ? anoAtual : "Todos");
    }

    private VBox buildFiltros() {
        // ── Linha 1: nome, tipo de ativo, tipo de transação ─────────────
        Label lNome = new Label("Ativo");
        lNome.getStyleClass().add("card-label");
        fNome = new TextField();
        fNome.setPromptText("Buscar por nome...");
        fNome.setStyle("-fx-font-size: 15px;");
        fNome.textProperty().addListener((o, a, b) -> aplicarFiltros());
        HBox.setHgrow(fNome, Priority.ALWAYS);
        VBox vNome = new VBox(4, lNome, fNome);
        HBox.setHgrow(vNome, Priority.ALWAYS);

        Label lTipoAtivo = new Label("Tipo de ativo");
        lTipoAtivo.getStyleClass().add("card-label");
        cbTipoAtivo = new ComboBox<>();
        cbTipoAtivo.getItems().addAll("Todos", "Renda Fixa", "Renda Variável", "Dólar");
        cbTipoAtivo.setValue("Todos");
        cbTipoAtivo.setMaxWidth(Double.MAX_VALUE);
        cbTipoAtivo.setStyle("-fx-font-size: 15px;");
        cbTipoAtivo.setOnAction(e -> aplicarFiltros());
        VBox vTipoAtivo = new VBox(4, lTipoAtivo, cbTipoAtivo);

        Label lTipoTrans = new Label("Tipo de transação");
        lTipoTrans.getStyleClass().add("card-label");
        cbTipoTransacao = new ComboBox<>();
        cbTipoTransacao.getItems().addAll("Todos", "Depósito", "Saque", "Compra", "Venda", "Dividendo");
        cbTipoTransacao.setValue("Todos");
        cbTipoTransacao.setMaxWidth(Double.MAX_VALUE);
        cbTipoTransacao.setStyle("-fx-font-size: 15px;");
        cbTipoTransacao.setOnAction(e -> aplicarFiltros());
        VBox vTipoTrans = new VBox(4, lTipoTrans, cbTipoTransacao);

        HBox row1 = new HBox(12, vNome, vTipoAtivo, vTipoTrans);
        row1.setAlignment(Pos.BOTTOM_LEFT);

        // ── Linha 2: mês, ano, valor mín, valor máx, limpar ─────────────
        Label lMes = new Label("Mês");
        lMes.getStyleClass().add("card-label");
        cbMes = new ComboBox<>();
        cbMes.getItems().addAll(MESES_NOME);
        cbMes.setValue("Todos");
        cbMes.setMaxWidth(Double.MAX_VALUE);
        cbMes.setStyle("-fx-font-size: 15px;");
        cbMes.setOnAction(e -> aplicarFiltros());
        VBox vMes = new VBox(4, lMes, cbMes);

        Label lAno = new Label("Ano");
        lAno.getStyleClass().add("card-label");
        cbAno = new ComboBox<>();
        cbAno.getItems().add("Todos");
        todasLinhas.stream()
            .map(LinhaTransacao::periodoAno)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .map(String::valueOf)
            .forEach(cbAno.getItems()::add);
        cbAno.setValue("Todos");
        cbAno.setMaxWidth(Double.MAX_VALUE);
        cbAno.setStyle("-fx-font-size: 15px;");
        cbAno.setOnAction(e -> aplicarFiltros());
        VBox vAno = new VBox(4, lAno, cbAno);

        Label lValMin = new Label("Valor mínimo");
        lValMin.getStyleClass().add("card-label");
        fValorMin = new TextField();
        fValorMin.setPromptText("Ex: 100,00");
        fValorMin.setStyle("-fx-font-size: 15px;");
        InputUtil.applyDecimalFilter(fValorMin);
        fValorMin.textProperty().addListener((o, a, b) -> aplicarFiltros());
        VBox vValMin = new VBox(4, lValMin, fValorMin);

        Label lValMax = new Label("Valor máximo");
        lValMax.getStyleClass().add("card-label");
        fValorMax = new TextField();
        fValorMax.setPromptText("Ex: 5.000,00");
        fValorMax.setStyle("-fx-font-size: 15px;");
        InputUtil.applyDecimalFilter(fValorMax);
        fValorMax.textProperty().addListener((o, a, b) -> aplicarFiltros());
        VBox vValMax = new VBox(4, lValMax, fValorMax);

        Label lBtnSpace = new Label(" ");
        lBtnSpace.getStyleClass().add("card-label");
        Button btnLimpar = new Button("Limpar filtros");
        btnLimpar.getStyleClass().add("btn-secondary");
        btnLimpar.setOnAction(e -> limparFiltros());
        VBox vBtn = new VBox(4, lBtnSpace, btnLimpar);
        vBtn.setAlignment(Pos.BOTTOM_LEFT);

        HBox row2 = new HBox(12, vMes, vAno, vValMin, vValMax, vBtn);
        row2.setAlignment(Pos.BOTTOM_LEFT);

        VBox filtros = new VBox(10, row1, row2);
        filtros.getStyleClass().add("card");
        return filtros;
    }

    private void limparFiltros() {
        fNome.clear();
        cbTipoAtivo.setValue("Todos");
        cbTipoTransacao.setValue("Todos");
        cbMes.setValue("Todos");
        cbAno.setValue("Todos");
        fValorMin.clear();
        fValorMax.clear();
    }

    private void aplicarFiltros() {
        String nome = fNome.getText().trim().toLowerCase();
        String tipoAtivo = cbTipoAtivo.getValue();
        String tipoTrans = cbTipoTransacao.getValue();
        int mesSel = Arrays.asList(MESES_NOME).indexOf(cbMes.getValue());
        String anoSel = cbAno.getValue() != null ? cbAno.getValue() : "Todos";
        double valMin = parseValor(fValorMin.getText());
        double valMax = parseValor(fValorMax.getText());

        List<LinhaTransacao> filtradas = todasLinhas.stream()
            .filter(l -> nome.isEmpty() || l.ativo().toLowerCase().contains(nome))
            .filter(l -> "Todos".equals(tipoAtivo) || tipoAtivoCombina(l.tipoAtivo(), tipoAtivo))
            .filter(l -> "Todos".equals(tipoTrans) || tipoTransCombina(l.tipo(), tipoTrans))
            .filter(l -> mesSel == 0 || l.periodoMes() == mesSel)
            .filter(l -> "Todos".equals(anoSel) || l.periodoAno() == Integer.parseInt(anoSel))
            .filter(l -> valMin < 0 || l.valor() >= valMin)
            .filter(l -> valMax < 0 || l.valor() <= valMax)
            .collect(Collectors.toList());

        table.getItems().setAll(filtradas);
    }

    private double parseValor(String text) {
        if (text == null || text.isBlank()) return -1;
        try { return InputUtil.parseDoubleField(text); }
        catch (NumberFormatException e) { return -1; }
    }

    private boolean tipoAtivoCombina(String tipoAtivo, String filtro) {
        return switch (filtro) {
            case "Renda Fixa"     -> "RENDA_FIXA".equals(tipoAtivo);
            case "Renda Variável" -> "RENDA_VARIAVEL".equals(tipoAtivo);
            case "Dólar"          -> "DOLAR".equals(tipoAtivo);
            default -> true;
        };
    }

    private boolean tipoTransCombina(String tipo, String filtro) {
        return switch (filtro) {
            case "Depósito"  -> "DEPOSITO".equals(tipo);
            case "Saque"     -> "SAQUE".equals(tipo);
            case "Compra"    -> "COMPRA".equals(tipo);
            case "Venda"     -> "VENDA".equals(tipo);
            case "Dividendo" -> "DIVIDENDO".equals(tipo);
            default -> true;
        };
    }

    private TableView<LinhaTransacao> buildTable() {
        TableView<LinhaTransacao> t = new TableView<>();
        t.getStyleClass().add("table-view");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label("Nenhuma transação encontrada para os filtros aplicados."));

        TableColumn<LinhaTransacao, String> cPer = new TableColumn<>("Período");
        cPer.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.diaMesAno(c.getValue().periodoDia(), c.getValue().periodoMes(), c.getValue().periodoAno())));
        cPer.setPrefWidth(120);

        TableColumn<LinhaTransacao, String> cAtivo = new TableColumn<>("Ativo");
        cAtivo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().ativo()));
        cAtivo.setPrefWidth(220);

        TableColumn<LinhaTransacao, String> cTipoAtivo = new TableColumn<>("Categoria");
        cTipoAtivo.setCellValueFactory(c -> new SimpleStringProperty(labelTipoAtivo(c.getValue().tipoAtivo())));
        cTipoAtivo.setPrefWidth(130);

        TableColumn<LinhaTransacao, String> cTipo = new TableColumn<>("Tipo");
        cTipo.setCellValueFactory(c -> new SimpleStringProperty(labelTipo(c.getValue().tipo())));
        cTipo.setPrefWidth(120);

        TableColumn<LinhaTransacao, String> cValor = new TableColumn<>("Valor");
        cValor.setCellValueFactory(c -> new SimpleStringProperty(FormatUtil.brl(c.getValue().valor())));
        cValor.setPrefWidth(140);
        cValor.setStyle("-fx-alignment: CENTER-RIGHT;");
        cValor.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                LinhaTransacao linha = getTableRow().getItem();
                boolean entrada = !linha.tipo().equals("SAQUE") && !linha.tipo().equals("VENDA");
                setText(item + (entrada ? " ▲" : " ▼"));
                setStyle("-fx-text-fill: " + (entrada ? "#2e7d32" : "#c62828") + "; -fx-alignment: CENTER-RIGHT;");
            }
        });

        TableColumn<LinhaTransacao, String> cNotas = new TableColumn<>("Notas");
        cNotas.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().notas() != null ? c.getValue().notas() : ""));

        t.getColumns().addAll(cPer, cAtivo, cTipoAtivo, cTipo, cValor, cNotas);
        return t;
    }

    private String labelTipo(String tipo) {
        return switch (tipo) {
            case "DEPOSITO"  -> "Depósito";
            case "SAQUE"     -> "Saque";
            case "COMPRA"    -> "Compra";
            case "VENDA"     -> "Venda";
            case "DIVIDENDO" -> "Dividendo";
            default -> tipo;
        };
    }

    private String labelTipoAtivo(String tipo) {
        return switch (tipo) {
            case "RENDA_FIXA"     -> "Renda Fixa";
            case "RENDA_VARIAVEL" -> "Renda Variável";
            case "DOLAR"          -> "Dólar";
            default -> tipo;
        };
    }

    private void carregarDados() {
        todasLinhas = new ArrayList<>();
        for (Investimento inv : invRepo.findAll()) {
            String nome = inv.getNome();
            String tipoAtivo = inv.getTipo().name();
            for (Movimentacao mov : movRepo.findByInvestimento(inv.getId())) {
                todasLinhas.add(new LinhaTransacao(
                        mov.getPeriodoDia(), mov.getPeriodoMes(), mov.getPeriodoAno(),
                        nome, tipoAtivo, mov.getTipoMov().name(),
                        mov.getValor(), mov.getNotas()));
            }
            for (AporteRv a : aporteRepo.findByInvestimento(inv.getId())) {
                todasLinhas.add(new LinhaTransacao(
                        a.getPeriodoDia(), a.getPeriodoMes(), a.getPeriodoAno(),
                        nome, tipoAtivo, a.getTipoOp().name(),
                        a.getValor(), a.getNotas()));
            }
        }
        todasLinhas.sort(Comparator
                .comparingInt(LinhaTransacao::periodoAno)
                .thenComparingInt(LinhaTransacao::periodoMes)
                .thenComparingInt(LinhaTransacao::periodoDia)
                .reversed());
    }
}
