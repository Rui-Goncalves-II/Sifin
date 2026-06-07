package br.investimentos.ui.rendavariavel;

import br.investimentos.model.AporteRv;
import br.investimentos.model.Investimento;
import br.investimentos.model.VacMensal;
import br.investimentos.model.enums.TipoOperacaoRv;
import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.service.BrapiService;
import br.investimentos.ui.util.FormatUtil;
import br.investimentos.ui.util.GlossarioTooltip;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import javafx.embed.swing.SwingNode;
import javax.swing.SwingUtilities;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import javafx.application.Platform;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.util.StringConverter;

public class RendaVariavelDetalhePanel extends BorderPane {

    private static final int ANO_TODOS = -1;

    private final Investimento inv;
    private final InvestimentoRepository invRepo;
    private final AporteRvRepository aporteRepo;
    private final VacMensalRepository vacRepo;
    private final RendaVariavelService rvSvc;
    private final CotacaoService cotacaoSvc;
    private final BrapiService brapiSvc;
    private final Consumer<Node> navigate;

    private int anoSelecionado;

    public RendaVariavelDetalhePanel(Investimento inv, InvestimentoRepository invRepo,
                                      AporteRvRepository aporteRepo, VacMensalRepository vacRepo,
                                      RendaVariavelService rvSvc, CotacaoService cotacaoSvc,
                                      BrapiService brapiSvc, Consumer<Node> navigate) {
        this.inv = inv; this.invRepo = invRepo; this.aporteRepo = aporteRepo;
        this.vacRepo = vacRepo; this.rvSvc = rvSvc; this.cotacaoSvc = cotacaoSvc;
        this.brapiSvc = brapiSvc; this.navigate = navigate;
        anoSelecionado = LocalDate.now().getYear();
        construir();
    }

    private List<Integer> anosComDados() {
        TreeSet<Integer> anos = new TreeSet<>();
        aporteRepo.findByInvestimento(inv.getId()).forEach(a -> anos.add(a.getPeriodoAno()));
        vacRepo.findByInvestimento(inv.getId()).forEach(v -> anos.add(v.getPeriodoAno()));
        anos.add(LocalDate.now().getYear());
        return new ArrayList<>(anos);
    }

    private ComboBox<Integer> buildYearCombo() {
        ComboBox<Integer> combo = new ComboBox<>();
        combo.setPrefWidth(130);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(Integer v) {
                if (v == null) return "";
                return v == ANO_TODOS ? "Total" : String.valueOf(v);
            }
            @Override public Integer fromString(String s) { return null; }
        });
        combo.getItems().addAll(anosComDados());
        combo.getItems().add(ANO_TODOS);
        combo.setValue(anoSelecionado);
        combo.setOnAction(e -> {
            if (combo.getValue() != null) { anoSelecionado = combo.getValue(); construir(); }
        });
        return combo;
    }

    private void buscarCotacaoBrapi(Button btnBrapi) {
        String notas = inv.getNotas();
        if (notas == null || notas.isBlank()) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Preencha o campo 'Notas / Ticker' do ativo com o código do papel (ex: HGLG11).",
                    ButtonType.OK);
            alert.setTitle("Ticker não encontrado");
            alert.showAndWait();
            return;
        }
        String ticker = notas.strip().split("\\s+")[0].toUpperCase();
        btnBrapi.setDisable(true);
        btnBrapi.setText("Buscando...");

        Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; })
                .submit(() -> {
                    var precoOpt = brapiSvc.buscarPreco(ticker);
                    Platform.runLater(() -> {
                        btnBrapi.setDisable(false);
                        btnBrapi.setText("Buscar via BRAPI");
                        if (precoOpt.isEmpty()) {
                            Alert err = new Alert(Alert.AlertType.ERROR,
                                    "Não foi possível obter a cotação para " + ticker + ".\n" +
                                    "Verifique o token nas Configurações e o código do ticker.",
                                    ButtonType.OK);
                            err.setTitle("Erro ao buscar cotação");
                            err.showAndWait();
                            return;
                        }
                        double preco = precoOpt.get();
                        LocalDate hoje = LocalDate.now();
                        String mesAnoStr = String.format("%02d/%d", hoje.getMonthValue(), hoje.getYear());
                        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                                ticker + ": R$ " + String.format("%.2f", preco).replace('.', ',') +
                                "\nSalvar como VAC de " + mesAnoStr + "?",
                                ButtonType.YES, ButtonType.NO);
                        conf.setTitle("Cotação encontrada");
                        conf.showAndWait().filter(r -> r == ButtonType.YES).ifPresent(r -> {
                            VacMensal vac = new VacMensal();
                            vac.setInvestimentoId(inv.getId());
                            vac.setPeriodoMes(hoje.getMonthValue());
                            vac.setPeriodoAno(hoje.getYear());
                            vac.setVac(preco);
                            vac.setFonte("BRAPI");
                            vacRepo.salvar(vac);
                            construir();
                        });
                    });
                });
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Button btnVoltar = new Button("← Voltar");
        btnVoltar.getStyleClass().add("btn-secondary");
        btnVoltar.setOnAction(e -> navigate.accept(new RendaVariavelListPanel(invRepo, aporteRepo, vacRepo, rvSvc, cotacaoSvc, brapiSvc, navigate)));
        Label title = new Label("💹 " + inv.getNome());
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnBrapi = new Button("Buscar via BRAPI");
        btnBrapi.getStyleClass().add("btn-secondary");
        btnBrapi.setOnAction(e -> buscarCotacaoBrapi(btnBrapi));
        Button btnVac = new Button("Atualizar VAC");
        btnVac.getStyleClass().add("btn-secondary");
        btnVac.setOnAction(e -> { new VacFormDialog(inv, vacRepo).showAndWait(); construir(); });
        Button btnOp = new Button("+ Operação");
        btnOp.getStyleClass().add("btn-primary");
        btnOp.setOnAction(e -> { new AporteRvFormDialog(inv, new AporteRv(), aporteRepo).showAndWait(); construir(); });
        header.getChildren().addAll(btnVoltar, title, spacer, buildYearCombo(), btnBrapi, btnVac, btnOp);
        setTop(header);

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    private VBox buildContent() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 24, 24));

        // Posição always all-time: VTP, QC, VMA, VAC, LEB, PTG
        var pos = rvSvc.calcular(inv.getId());

        // Operações filtradas pelo ano selecionado
        List<AporteRv> opsDoAno = (anoSelecionado != ANO_TODOS)
                ? aporteRepo.findByInvestimentoEAno(inv.getId(), anoSelecionado)
                : aporteRepo.findByInvestimento(inv.getId());

        // Dividendos do ano/total
        double dAno = opsDoAno.stream()
                .filter(a -> a.getTipoOp() == TipoOperacaoRv.DIVIDENDO)
                .mapToDouble(AporteRv::getValor).sum();
        String dLabel = (anoSelecionado != ANO_TODOS)
                ? "Dividendos " + anoSelecionado
                : "Dividendos (D)";

        // VAC filtrado pelo ano para gráfico
        List<VacMensal> vacsDoAno = vacRepo.findByInvestimento(inv.getId()).stream()
                .filter(v -> anoSelecionado == ANO_TODOS || v.getPeriodoAno() == anoSelecionado)
                .collect(Collectors.toList());

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        ColumnConstraints cc = new ColumnConstraints(); cc.setPercentWidth(16.66); cc.setHgrow(Priority.ALWAYS);
        for (int i = 0; i < 6; i++) grid.getColumnConstraints().add(cc);

        // Row 0: posição all-time (exceto D que é filtrado por ano)
        grid.add(metricCard("VTP", FormatUtil.brl(pos.vtp()), "neutral"), 0, 0);
        grid.add(metricCard("QC", FormatUtil.qtd(pos.qc()), "neutral"), 1, 0);
        grid.add(metricCard("VMA", FormatUtil.brl(pos.vma()), "neutral"), 2, 0);
        grid.add(metricCard("VAC Atual", FormatUtil.brl(pos.vac()), "neutral"), 3, 0);
        grid.add(metricCard(dLabel, FormatUtil.brl(dAno), "positive"), 4, 0);
        grid.add(metricCard("PTG", FormatUtil.brl(pos.ptg()), "neutral"), 5, 0);

        // Row 1: métricas de ganho/perda all-time
        grid.add(metricCard("LEB", FormatUtil.brl(pos.leb()), pos.leb() >= 0 ? "positive" : "negative"), 0, 1);
        grid.add(metricCard("LEB %", FormatUtil.pct(pos.lebPct()), pos.lebPct() >= 0 ? "positive" : "negative"), 1, 1);

        grid.add(metricCard("LEA", FormatUtil.brl(pos.lea()), pos.lea() >= 0 ? "positive" : "negative"), 2, 1);
        grid.add(metricCard("LEA %", FormatUtil.pct(pos.leaPct()), pos.leaPct() >= 0 ? "positive" : "negative"), 3, 1);

        // Tabela de operações
        String opLabel = (anoSelecionado != ANO_TODOS)
                ? "Operações — " + anoSelecionado
                : "Operações — Total";
        Label opTitle = new Label(opLabel);
        opTitle.getStyleClass().add("section-title");

        TableView<AporteRv> opTable = new TableView<>();
        opTable.getStyleClass().add("table-view");
        opTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        opTable.setPrefHeight(300);

        TableColumn<AporteRv, String> cPer = new TableColumn<>("Período");
        cPer.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().getPeriodoMes(), c.getValue().getPeriodoAno())));
        cPer.setPrefWidth(90);

        TableColumn<AporteRv, String> cTipo = new TableColumn<>("Tipo");
        cTipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTipoOp().label()));
        cTipo.setPrefWidth(90);

        TableColumn<AporteRv, String> cQtd = new TableColumn<>("Qtd.");
        cQtd.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getQuantidade() != null ? FormatUtil.qtd(c.getValue().getQuantidade()) : "—"));
        cQtd.setPrefWidth(90);

        TableColumn<AporteRv, String> cPreco = new TableColumn<>("Preço/Cota");
        cPreco.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getPrecoPorCota() != null ? FormatUtil.brl(c.getValue().getPrecoPorCota()) : "—"));
        cPreco.setPrefWidth(110);

        TableColumn<AporteRv, String> cValor = new TableColumn<>("Valor");
        cValor.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(c.getValue().getValor())));
        cValor.setPrefWidth(120);
        cValor.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                boolean entrada = getTableRow().getItem().getTipoOp() != TipoOperacaoRv.VENDA;
                setText(item + (entrada ? " ▲" : " ▼"));
                setStyle("-fx-text-fill: " + (entrada ? "#2e7d32" : "#c62828") + ";");
            }
        });

        TableColumn<AporteRv, Void> cAcoes = new TableColumn<>("Ações");
        cAcoes.setMinWidth(160);
        cAcoes.setPrefWidth(160);
        cAcoes.setMaxWidth(160);
        cAcoes.setCellFactory(tc -> new TableCell<>() {
            final Button edit = new Button("✏ Editar");
            final Button del  = new Button("🗑 Excluir");
            {
                edit.getStyleClass().add("btn-secondary");
                del.getStyleClass().add("btn-danger");
                edit.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");
                del.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");
                edit.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                del.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                edit.setOnAction(e -> {
                    AporteRv a = getTableView().getItems().get(getIndex());
                    new AporteRvFormDialog(inv, a, aporteRepo).showAndWait();
                    construir();
                });
                del.setOnAction(e -> {
                    AporteRv a = getTableView().getItems().get(getIndex());
                    Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Remover operação?", ButtonType.YES, ButtonType.NO);
                    conf.showAndWait().filter(r -> r == ButtonType.YES).ifPresent(r -> { aporteRepo.deletar(a.getId()); construir(); });
                });
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : new HBox(6, edit, del)); }
        });

        opTable.getColumns().addAll(cPer, cTipo, cQtd, cPreco, cValor, cAcoes);
        List<AporteRv> opsTabela = new ArrayList<>(opsDoAno);
        Collections.reverse(opsTabela);
        opTable.getItems().addAll(opsTabela);

        // ── Histórico de VAC ─────────────────────────────────────────────
        String vacHistLabel = (anoSelecionado != ANO_TODOS)
                ? "Histórico de VAC — " + anoSelecionado
                : "Histórico de VAC — Total";
        Label vacHistTitle = new Label(vacHistLabel);
        vacHistTitle.getStyleClass().add("section-title");

        TableView<VacMensal> vacTable = new TableView<>();
        vacTable.getStyleClass().add("table-view");
        vacTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        vacTable.setPrefHeight(200);

        TableColumn<VacMensal, String> vcPer = new TableColumn<>("Período");
        vcPer.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().getPeriodoMes(), c.getValue().getPeriodoAno())));
        vcPer.setPrefWidth(100);

        TableColumn<VacMensal, String> vcVac = new TableColumn<>("VAC");
        vcVac.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().getVac())));
        vcVac.setPrefWidth(130);

        TableColumn<VacMensal, String> vcFonte = new TableColumn<>("Fonte");
        vcFonte.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getFonte() != null ? c.getValue().getFonte() : "MANUAL"));
        vcFonte.setPrefWidth(90);

        TableColumn<VacMensal, Void> vcAcoes = new TableColumn<>("Ações");
        vcAcoes.setMinWidth(160); vcAcoes.setPrefWidth(160); vcAcoes.setMaxWidth(160);
        vcAcoes.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("✏ Editar");
            private final Button btnDel  = new Button("🗑 Excluir");
            {
                btnEdit.getStyleClass().add("btn-secondary");
                btnDel.getStyleClass().add("btn-danger");
                btnEdit.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");
                btnDel.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");
                btnEdit.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                btnDel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

                btnEdit.setOnAction(e -> {
                    VacMensal v = getTableView().getItems().get(getIndex());
                    new VacFormDialog(inv, vacRepo, v).showAndWait();
                    construir();
                });
                btnDel.setOnAction(e -> {
                    VacMensal v = getTableView().getItems().get(getIndex());
                    Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                            "Excluir VAC de " + FormatUtil.mesAno(v.getPeriodoMes(), v.getPeriodoAno()) + "?",
                            ButtonType.YES, ButtonType.NO);
                    conf.setTitle("Confirmar exclusão");
                    conf.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) { vacRepo.deletar(v.getId()); construir(); }
                    });
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : new HBox(6, btnEdit, btnDel));
            }
        });

        vacTable.getColumns().addAll(vcPer, vcVac, vcFonte, vcAcoes);
        vacTable.getItems().addAll(vacsDoAno);

        HBox graficos = buildGraficos(vacsDoAno, opsDoAno);
        content.getChildren().addAll(grid, graficos, vacHistTitle, vacTable, opTitle, opTable);
        return content;
    }

    private HBox buildGraficos(List<VacMensal> vacs, List<AporteRv> aportes) {
        java.awt.Color bgCard  = new java.awt.Color(0x16, 0x1b, 0x22);
        java.awt.Color border  = new java.awt.Color(0x2a, 0x34, 0x41);
        java.awt.Color textMut = new java.awt.Color(0x7d, 0x8f, 0xa0);
        java.awt.Color amber   = new java.awt.Color(0xe3, 0xb3, 0x41);
        java.awt.Color blue    = new java.awt.Color(0x58, 0xa6, 0xff);
        java.awt.Font  fontXs   = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        java.awt.Font  fontBold = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);
        java.awt.BasicStroke stroke = new java.awt.BasicStroke(
                1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND);
        java.awt.geom.Ellipse2D.Double dot = new java.awt.geom.Ellipse2D.Double(-5, -5, 10, 10);

        // ── VAC por Mês ───────────────────────────────────────────────────
        DefaultCategoryDataset vacDs = new DefaultCategoryDataset();
        for (VacMensal v : vacs)
            vacDs.addValue(v.getVac(), "VAC", FormatUtil.mesAno(v.getPeriodoMes(), v.getPeriodoAno()));

        org.jfree.chart.renderer.category.LineAndShapeRenderer vacRend =
            new org.jfree.chart.renderer.category.LineAndShapeRenderer() {
                @Override public java.awt.Paint getItemPaint(int row, int col) { return blue; }
                @Override public java.awt.Paint getItemFillPaint(int row, int col) { return blue; }
            };
        vacRend.setDefaultLinesVisible(true); vacRend.setDefaultShapesVisible(true);
        vacRend.setDefaultShapesFilled(true); vacRend.setUseFillPaint(true);
        vacRend.setDefaultShape(dot); vacRend.setDefaultStroke(stroke);

        org.jfree.chart.axis.CategoryAxis xV = new org.jfree.chart.axis.CategoryAxis(null);
        xV.setTickLabelPaint(textMut); xV.setTickLabelFont(fontXs);
        xV.setAxisLinePaint(border); xV.setTickMarkPaint(border);
        org.jfree.chart.axis.NumberAxis yV = new org.jfree.chart.axis.NumberAxis("R$");
        yV.setTickLabelPaint(textMut); yV.setTickLabelFont(fontXs);
        yV.setAxisLinePaint(border); yV.setTickMarkPaint(border);
        yV.setAutoRangeIncludesZero(false);

        org.jfree.chart.plot.CategoryPlot vacPlot = new org.jfree.chart.plot.CategoryPlot(vacDs, xV, yV, vacRend);
        vacPlot.setBackgroundPaint(bgCard); vacPlot.setOutlinePaint(border);
        vacPlot.setRangeGridlinePaint(border); vacPlot.setDomainGridlinesVisible(false);

        String vacTitle = (anoSelecionado != ANO_TODOS)
                ? "VAC por Mês — " + anoSelecionado
                : "VAC por Mês — Total";
        JFreeChart vacChart = new JFreeChart(vacTitle, fontBold, vacPlot, false);
        vacChart.setBackgroundPaint(bgCard); vacChart.setBorderVisible(false);
        vacChart.getTitle().setPaint(textMut); vacChart.getTitle().setFont(fontBold);

        SwingNode vacNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(vacChart); cp.setBackground(bgCard); vacNode.setContent(cp);
        });
        StackPane vacPane = new StackPane(vacNode); vacPane.setPrefSize(400, 220);

        // ── Dividendos por Período ────────────────────────────────────────
        Map<String, Double> divMap = new LinkedHashMap<>();
        aportes.stream()
            .filter(a -> a.getTipoOp() == TipoOperacaoRv.DIVIDENDO)
            .sorted(Comparator.comparingInt((AporteRv a) -> a.getPeriodoAno() * 100 + a.getPeriodoMes()))
            .forEach(a -> divMap.merge(
                FormatUtil.mesAno(a.getPeriodoMes(), a.getPeriodoAno()), a.getValor(), Double::sum));

        DefaultCategoryDataset divDs = new DefaultCategoryDataset();
        divMap.forEach((key, val) -> divDs.addValue(val, "Dividendos", key));

        org.jfree.chart.renderer.category.LineAndShapeRenderer divRend =
            new org.jfree.chart.renderer.category.LineAndShapeRenderer() {
                @Override public java.awt.Paint getItemPaint(int row, int col) { return amber; }
                @Override public java.awt.Paint getItemFillPaint(int row, int col) { return amber; }
            };
        divRend.setDefaultLinesVisible(true); divRend.setDefaultShapesVisible(true);
        divRend.setDefaultShapesFilled(true); divRend.setUseFillPaint(true);
        divRend.setDefaultShape(dot); divRend.setDefaultStroke(stroke);

        org.jfree.chart.axis.CategoryAxis xD = new org.jfree.chart.axis.CategoryAxis(null);
        xD.setTickLabelPaint(textMut); xD.setTickLabelFont(fontXs);
        xD.setAxisLinePaint(border); xD.setTickMarkPaint(border);
        org.jfree.chart.axis.NumberAxis yD = new org.jfree.chart.axis.NumberAxis("R$");
        yD.setTickLabelPaint(textMut); yD.setTickLabelFont(fontXs);
        yD.setAxisLinePaint(border); yD.setTickMarkPaint(border);

        org.jfree.chart.plot.CategoryPlot divPlot = new org.jfree.chart.plot.CategoryPlot(divDs, xD, yD, divRend);
        divPlot.setBackgroundPaint(bgCard); divPlot.setOutlinePaint(border);
        divPlot.setRangeGridlinePaint(border); divPlot.setDomainGridlinesVisible(false);

        String divTitle = (anoSelecionado != ANO_TODOS)
                ? "Dividendos — " + anoSelecionado
                : "Dividendos por Período";
        JFreeChart divChart = new JFreeChart(divTitle, fontBold, divPlot, false);
        divChart.setBackgroundPaint(bgCard); divChart.setBorderVisible(false);
        divChart.getTitle().setPaint(textMut); divChart.getTitle().setFont(fontBold);

        SwingNode divNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(divChart); cp.setBackground(bgCard); divNode.setContent(cp);
        });
        StackPane divPane = new StackPane(divNode); divPane.setPrefSize(400, 220);

        VBox vacBox = new VBox(vacPane);
        vacBox.getStyleClass().add("card"); vacBox.setPadding(Insets.EMPTY);
        HBox.setHgrow(vacBox, Priority.ALWAYS);

        VBox divBox = new VBox(divPane);
        divBox.getStyleClass().add("card"); divBox.setPadding(Insets.EMPTY);
        HBox.setHgrow(divBox, Priority.ALWAYS);

        return new HBox(12, vacBox, divBox);
    }

    private VBox metricCard(String title, String value, String valueStyle) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        GlossarioTooltip.aplicar(t, title);
        Label v = new Label(value);
        v.getStyleClass().addAll("card-value-sm", valueStyle);
        card.getChildren().addAll(t, v);
        return card;
    }
}
