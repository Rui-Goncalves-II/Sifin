package br.investimentos.ui.gastos;

import br.investimentos.service.GastosService;
import br.investimentos.service.GastosService.ResumoMensal;
import br.investimentos.ui.util.FormatUtil;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.time.LocalDate;
import java.util.List;

public class GastosDashboardPanel extends BorderPane {

    private static final String[] MESES_ABREV = {
        "", "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
        "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    };

    private final GastosService svc;

    private HBox yearBar;
    private VBox contentBox;
    private int anoSelecionado;

    public GastosDashboardPanel(GastosService svc) {
        this.svc = svc;
        this.anoSelecionado = LocalDate.now().getYear();
        construir();
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("💸 Gastos — Dashboard");
        title.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        yearBar = new HBox(6);
        yearBar.setAlignment(Pos.CENTER_LEFT);
        buildYearBar();

        header.getChildren().addAll(title, spacer, yearBar);
        setTop(header);

        contentBox = new VBox(16);
        contentBox.setPadding(new Insets(20, 24, 24, 24));

        ScrollPane scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);

        refresh();
    }

    private void buildYearBar() {
        yearBar.getChildren().clear();
        List<Integer> anos = svc.anosComDados();
        int atual = LocalDate.now().getYear();
        if (!anos.contains(atual)) anos.add(0, atual);

        for (int ano : anos) {
            Button btn = new Button(String.valueOf(ano));
            btn.getStyleClass().add("year-btn");
            if (ano == anoSelecionado) btn.getStyleClass().add("active");
            btn.setOnAction(e -> { anoSelecionado = ano; buildYearBar(); refresh(); });
            yearBar.getChildren().add(btn);
        }

        Button todos = new Button("Todos os anos");
        todos.getStyleClass().add("year-btn");
        if (anoSelecionado == GastosService.ANO_TODOS) todos.getStyleClass().add("active");
        todos.setOnAction(e -> { anoSelecionado = GastosService.ANO_TODOS; buildYearBar(); refresh(); });
        yearBar.getChildren().add(todos);
    }

    public void refresh() {
        double gat = svc.calcularGAT(anoSelecionado);
        double gdt = svc.calcularGDT(anoSelecionado);
        double gmt = svc.calcularGMT(anoSelecionado);
        double gt  = gat + gdt + gmt;
        double gmp = svc.calcularGMP();

        List<ResumoMensal> porMes = svc.calcularPorMes(anoSelecionado);

        contentBox.getChildren().clear();
        contentBox.getChildren().add(buildKpiRow(gt, gat, gdt, gmt));
        contentBox.getChildren().add(buildGmpCard(gmp));
        contentBox.getChildren().add(buildMultiLineChart(porMes));
        contentBox.getChildren().add(buildTotalLineChart(porMes));
        contentBox.getChildren().add(buildTabelaMensal(porMes));
    }

    // ── KPI cards ────────────────────────────────────────────────────────

    private HBox buildKpiRow(double gt, double gat, double gdt, double gmt) {
        HBox row = new HBox(12);

        VBox kGT  = makeKpiCard("Gastos Totais",      FormatUtil.brl(gt),  "GT",  "#f0883e");
        VBox kGAT = makeKpiCard("Gastos Alimentar",   FormatUtil.brl(gat), "GAT", "#e3b341");
        VBox kGDT = makeKpiCard("Gastos Diversos",    FormatUtil.brl(gdt), "GDT", "#58a6ff");
        VBox kGMT = makeKpiCard("Gastos Mensalidades",FormatUtil.brl(gmt), "GMT", "#bc8cff");

        for (VBox card : new VBox[]{kGT, kGAT, kGDT, kGMT}) {
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
        }
        row.getChildren().addAll(kGT, kGAT, kGDT, kGMT);
        return row;
    }

    private VBox makeKpiCard(String label, String value, String sublabel, String accent) {
        VBox card = new VBox(6);
        card.getStyleClass().add("kpi-card");
        card.setStyle(String.format(
                "-fx-border-color: #2a3441 #2a3441 #2a3441 %s; -fx-border-width: 1 1 1 3; " +
                "-fx-border-radius: 0 10 10 0; -fx-background-radius: 0 10 10 0;", accent));

        Label lbl = new Label(label);
        lbl.getStyleClass().add("kpi-label");

        Label val = new Label(value);
        val.getStyleClass().add("kpi-value");

        Label sub = new Label(sublabel);
        sub.getStyleClass().add("card-label");

        card.getChildren().addAll(lbl, val, sub);
        return card;
    }

    // ── GMP card ─────────────────────────────────────────────────────────

    private HBox buildGmpCard(double gmp) {
        int anoAtual = java.time.LocalDate.now().getYear();

        VBox card = new VBox(6);
        card.getStyleClass().add("kpi-card");
        card.setStyle("-fx-border-color: #2a3441 #2a3441 #2a3441 #3fb950; " +
                "-fx-border-width: 1 1 1 3; -fx-border-radius: 0 10 10 0; -fx-background-radius: 0 10 10 0;");

        Label lbl = new Label("Previsão de mensalidades para " + anoAtual);
        lbl.getStyleClass().add("kpi-label");

        Label val = new Label(FormatUtil.brl(gmp));
        val.getStyleClass().add("kpi-value");

        Label sub = new Label("GMP — do início até Dez/" + anoAtual);
        sub.getStyleClass().add("card-label");

        card.getChildren().addAll(lbl, val, sub);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);

        HBox row = new HBox(card);
        return row;
    }

    // ── Gráfico multi-série (GAT + GDT + GMT) ────────────────────────────

    private VBox buildMultiLineChart(List<ResumoMensal> dados) {
        java.awt.Color bgCard   = new java.awt.Color(0x16, 0x1b, 0x22);
        java.awt.Color border   = new java.awt.Color(0x2a, 0x34, 0x41);
        java.awt.Color textMut  = new java.awt.Color(0x7d, 0x8f, 0xa0);
        java.awt.Color amber    = new java.awt.Color(0xe3, 0xb3, 0x41);
        java.awt.Color blue     = new java.awt.Color(0x58, 0xa6, 0xff);
        java.awt.Color purple   = new java.awt.Color(0xbc, 0x8c, 0xff);
        java.awt.Font  fontXs   = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        java.awt.Font  fontBold = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);
        java.awt.BasicStroke stroke = new java.awt.BasicStroke(
                1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND);
        java.awt.geom.Ellipse2D.Double dot = new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8);

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ResumoMensal r : dados) {
            String label = mesLabel(r.mes(), r.ano());
            ds.addValue(r.alimentar(),   "Alimentar",    label);
            ds.addValue(r.diverso(),     "Diversos",     label);
            ds.addValue(r.mensalidade(), "Mensalidades", label);
        }

        org.jfree.chart.axis.CategoryAxis xAxis = new org.jfree.chart.axis.CategoryAxis(null);
        xAxis.setTickLabelPaint(textMut);
        xAxis.setTickLabelFont(fontXs);
        xAxis.setAxisLinePaint(border);
        xAxis.setTickMarkPaint(border);

        org.jfree.chart.axis.NumberAxis yAxis = new org.jfree.chart.axis.NumberAxis("R$");
        yAxis.setTickLabelPaint(textMut);
        yAxis.setTickLabelFont(fontXs);
        yAxis.setAxisLinePaint(border);
        yAxis.setTickMarkPaint(border);
        yAxis.setLabelPaint(textMut);
        yAxis.setLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        yAxis.setAutoRangeIncludesZero(true);

        java.awt.Color[] colors = {amber, blue, purple};
        org.jfree.chart.renderer.category.LineAndShapeRenderer renderer =
                new org.jfree.chart.renderer.category.LineAndShapeRenderer() {
                    @Override public java.awt.Paint getItemPaint(int row, int col) { return colors[row]; }
                    @Override public java.awt.Paint getItemFillPaint(int row, int col) { return colors[row]; }
                };
        renderer.setDefaultLinesVisible(true);
        renderer.setDefaultShapesVisible(true);
        renderer.setDefaultShapesFilled(true);
        renderer.setUseFillPaint(true);
        renderer.setDefaultShape(dot);
        renderer.setDefaultStroke(stroke);

        org.jfree.chart.plot.CategoryPlot plot = new org.jfree.chart.plot.CategoryPlot(ds, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(bgCard);
        plot.setOutlinePaint(border);
        plot.setRangeGridlinePaint(border);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeZeroBaselineVisible(true);
        plot.setRangeZeroBaselinePaint(textMut);

        JFreeChart chart = new JFreeChart("Gastos por Tipo e Mês", fontBold, plot, true);
        chart.setBackgroundPaint(bgCard);
        chart.setBorderVisible(false);
        chart.getTitle().setPaint(textMut);
        chart.getTitle().setFont(fontBold);

        org.jfree.chart.title.LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setBackgroundPaint(bgCard);
            legend.setItemPaint(textMut);
            legend.setItemFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        }

        SwingNode node = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(chart);
            cp.setBackground(bgCard);
            node.setContent(cp);
        });

        StackPane wrapper = new StackPane(node);
        wrapper.setPrefHeight(260);

        VBox box = new VBox(wrapper);
        box.getStyleClass().add("card");
        box.setPadding(Insets.EMPTY);
        return box;
    }

    // ── Gráfico GT por mês ───────────────────────────────────────────────

    private VBox buildTotalLineChart(List<ResumoMensal> dados) {
        java.awt.Color bgCard   = new java.awt.Color(0x16, 0x1b, 0x22);
        java.awt.Color border   = new java.awt.Color(0x2a, 0x34, 0x41);
        java.awt.Color textMut  = new java.awt.Color(0x7d, 0x8f, 0xa0);
        java.awt.Color red      = new java.awt.Color(0xf0, 0x88, 0x3e);
        java.awt.Font  fontXs   = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        java.awt.Font  fontBold = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);
        java.awt.BasicStroke stroke = new java.awt.BasicStroke(
                2.0f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND);
        java.awt.geom.Ellipse2D.Double dot = new java.awt.geom.Ellipse2D.Double(-5, -5, 10, 10);

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ResumoMensal r : dados) {
            ds.addValue(r.total(), "Total", mesLabel(r.mes(), r.ano()));
        }

        org.jfree.chart.axis.CategoryAxis xAxis = new org.jfree.chart.axis.CategoryAxis(null);
        xAxis.setTickLabelPaint(textMut);
        xAxis.setTickLabelFont(fontXs);
        xAxis.setAxisLinePaint(border);
        xAxis.setTickMarkPaint(border);

        org.jfree.chart.axis.NumberAxis yAxis = new org.jfree.chart.axis.NumberAxis("R$");
        yAxis.setTickLabelPaint(textMut);
        yAxis.setTickLabelFont(fontXs);
        yAxis.setAxisLinePaint(border);
        yAxis.setTickMarkPaint(border);
        yAxis.setLabelPaint(textMut);
        yAxis.setLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        yAxis.setAutoRangeIncludesZero(true);

        org.jfree.chart.renderer.category.LineAndShapeRenderer renderer =
                new org.jfree.chart.renderer.category.LineAndShapeRenderer() {
                    @Override public java.awt.Paint getItemPaint(int row, int col) { return red; }
                    @Override public java.awt.Paint getItemFillPaint(int row, int col) { return red; }
                };
        renderer.setDefaultLinesVisible(true);
        renderer.setDefaultShapesVisible(true);
        renderer.setDefaultShapesFilled(true);
        renderer.setUseFillPaint(true);
        renderer.setDefaultShape(dot);
        renderer.setDefaultStroke(stroke);

        org.jfree.chart.plot.CategoryPlot plot = new org.jfree.chart.plot.CategoryPlot(ds, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(bgCard);
        plot.setOutlinePaint(border);
        plot.setRangeGridlinePaint(border);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeZeroBaselineVisible(false);

        JFreeChart chart = new JFreeChart("Gastos Totais por Mês (GT)", fontBold, plot, false);
        chart.setBackgroundPaint(bgCard);
        chart.setBorderVisible(false);
        chart.getTitle().setPaint(textMut);
        chart.getTitle().setFont(fontBold);

        SwingNode node = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(chart);
            cp.setBackground(bgCard);
            node.setContent(cp);
        });

        StackPane wrapper = new StackPane(node);
        wrapper.setPrefHeight(220);

        VBox box = new VBox(wrapper);
        box.getStyleClass().add("card");
        box.setPadding(Insets.EMPTY);
        return box;
    }

    // ── Tabela mensal ────────────────────────────────────────────────────

    private VBox buildTabelaMensal(List<ResumoMensal> dados) {
        TableView<ResumoMensal> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Sem gastos no período selecionado."));

        TableColumn<ResumoMensal, String> cMes = new TableColumn<>("Mês");
        cMes.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().mes(), c.getValue().ano())));
        cMes.setPrefWidth(90);

        TableColumn<ResumoMensal, String> cAli = new TableColumn<>("Alimentar");
        cAli.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().alimentar())));
        cAli.setStyle("-fx-alignment: CENTER-RIGHT;");
        cAli.setPrefWidth(130);

        TableColumn<ResumoMensal, String> cDiv = new TableColumn<>("Diversos");
        cDiv.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().diverso())));
        cDiv.setStyle("-fx-alignment: CENTER-RIGHT;");
        cDiv.setPrefWidth(130);

        TableColumn<ResumoMensal, String> cMen = new TableColumn<>("Mensalidades");
        cMen.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().mensalidade())));
        cMen.setStyle("-fx-alignment: CENTER-RIGHT;");
        cMen.setPrefWidth(130);

        TableColumn<ResumoMensal, String> cTot = new TableColumn<>("Total");
        cTot.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().total())));
        cTot.setStyle("-fx-alignment: CENTER-RIGHT;");
        cTot.setPrefWidth(130);
        cTot.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
            }
        });

        table.getColumns().addAll(cMes, cAli, cDiv, cMen, cTot);
        table.getItems().addAll(dados);

        int rowH = 32, headerH = 30;
        table.setPrefHeight(Math.max(3, dados.size()) * rowH + headerH);

        Label titleLbl = new Label("Resumo Mensal");
        titleLbl.getStyleClass().add("section-title");
        VBox box = new VBox(8, titleLbl, table);
        box.getStyleClass().add("card");
        return box;
    }

    private String mesLabel(int mes, int ano) {
        return MESES_ABREV[mes] + "/" + (ano % 100);
    }
}
