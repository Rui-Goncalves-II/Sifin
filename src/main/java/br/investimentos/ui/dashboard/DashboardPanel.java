package br.investimentos.ui.dashboard;

import br.investimentos.model.CotacaoDolar;
import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.service.GastosService.ResumoMensal;
import br.investimentos.ui.util.FormatUtil;
import br.investimentos.ui.util.GlossarioTooltip;
import br.investimentos.ui.util.UIUtil;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.image.ImageView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.util.StringConverter;

public class DashboardPanel extends BorderPane {

    private final InvestimentoRepository invRepo;
    private final AporteRvRepository aporteRepo;
    private final VtaMensalRepository vtaRepo;
    private final VacMensalRepository vacRepo;
    private final RendimentoService rendSvc;
    private final RendaVariavelService rvSvc;
    private final SaldoService saldoSvc;
    private final ConsolidacaoService consolSvc;
    private final CotacaoService cotacaoSvc;
    private final AlertaService alertaSvc;
    private final GastosService gastosSvc;
    private final Consumer<Node> navigate;

    private Label usdLabel;
    private ComboBox<Integer> yearCombo;
    private VBox alertBox;
    private VBox contentBox;
    private int anoSelecionado;
    private final Set<TipoInvestimento> tiposFiltro = EnumSet.allOf(TipoInvestimento.class);

    public DashboardPanel(InvestimentoRepository invRepo,
                          AporteRvRepository aporteRepo, VtaMensalRepository vtaRepo,
                          VacMensalRepository vacRepo, RendimentoService rendSvc,
                          RendaVariavelService rvSvc, SaldoService saldoSvc,
                          ConsolidacaoService consolSvc, CotacaoService cotacaoSvc,
                          AlertaService alertaSvc, GastosService gastosSvc, Consumer<Node> navigate) {
        this.invRepo = invRepo; this.aporteRepo = aporteRepo;
        this.vtaRepo = vtaRepo; this.vacRepo = vacRepo; this.rendSvc = rendSvc;
        this.rvSvc = rvSvc; this.saldoSvc = saldoSvc; this.consolSvc = consolSvc;
        this.cotacaoSvc = cotacaoSvc; this.alertaSvc = alertaSvc;
        this.gastosSvc = gastosSvc; this.navigate = navigate;

        anoSelecionado = LocalDate.now().getYear();
        construir();
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Dashboard");
        title.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        usdLabel = new Label("💰 USD: carregando...");
        usdLabel.getStyleClass().add("usd-badge");

        yearCombo = new ComboBox<>();
        yearCombo.setPrefWidth(160);
        yearCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Integer v) {
                if (v == null) return "";
                return v == ConsolidacaoService.ANO_TODOS ? "Todos os anos" : String.valueOf(v);
            }
            @Override public Integer fromString(String s) { return null; }
        });
        yearCombo.setOnAction(e -> {
            if (yearCombo.getValue() != null) { anoSelecionado = yearCombo.getValue(); refresh(); }
        });
        populateYearCombo();

        header.getChildren().addAll(title, spacer, yearCombo, usdLabel);
        setTop(header);

        alertBox = new VBox(4);

        contentBox = new VBox(16);
        contentBox.setPadding(new Insets(20, 24, 24, 24));

        ScrollPane scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);

        refresh();
        atualizarUsd();
    }

    private void populateYearCombo() {
        List<Integer> anos = consolSvc.anosComDados();
        int atual = LocalDate.now().getYear();
        if (!anos.contains(atual)) anos.add(0, atual);
        yearCombo.getItems().setAll(anos);
        yearCombo.getItems().add(ConsolidacaoService.ANO_TODOS);
        yearCombo.setValue(anoSelecionado);
    }

    /** Chamado pelo callback de cotação (já na thread JavaFX via Platform.runLater). */
    public void onCotacaoAtualizada() {
        refresh();
        atualizarUsd();
    }

    private void refresh() {
        ConsolidacaoService.ResultadoConsolidado r = consolSvc.calcular(anoSelecionado, tiposFiltro);
        double dolarBrl = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorCompra).orElse(0.0);
        double dolarTotal = tiposFiltro.contains(TipoInvestimento.DOLAR) ? consolSvc.calcularDolarTotal(dolarBrl) : 0.0;
        double dolarInvestidoBrl = consolSvc.calcularDolarInvestidoBrl(anoSelecionado, dolarBrl);
        double ptaTotal = r.pta() + dolarTotal;
        double vtiaTotal = r.vtia() + dolarInvestidoBrl;
        Double pcpaTotal = ptaTotal > 0 ? (r.vtra() / ptaTotal) * 100 : null;
        Double pratTotal = vtiaTotal > 0 ? (r.vtra() / vtiaTotal) * 100 : null;
        LocalDate hoje = LocalDate.now();
        double aporteMes = consolSvc.calcularAportesDoAno(anoSelecionado, tiposFiltro, dolarBrl);

        int gastoAno = anoSelecionado == ConsolidacaoService.ANO_TODOS ? GastosService.ANO_TODOS : anoSelecionado;
        double gt  = gastosSvc.calcularGT(gastoAno);
        double gat = gastosSvc.calcularGAT(gastoAno);
        double gdt = gastosSvc.calcularGDT(gastoAno);
        double gmt = gastosSvc.calcularGMT(gastoAno);
        List<ResumoMensal> gastosPorMes = gastosSvc.calcularPorMes(gastoAno);

        contentBox.getChildren().clear();

        atualizarAlertas();
        contentBox.getChildren().add(alertBox);
        contentBox.getChildren().add(buildPillsBar(r, dolarTotal));
        contentBox.getChildren().add(buildKpiGrid(r, aporteMes, ptaTotal, vtiaTotal));
        contentBox.getChildren().add(buildGastosKpiRow(gt, gat, gdt, gmt));

        HBox rendRow = buildRendimentoCard(pcpaTotal, pratTotal, r.todosOsAnos());
        if (!rendRow.getChildren().isEmpty()) contentBox.getChildren().add(rendRow);

        contentBox.getChildren().add(buildMiniTypeCards(r, dolarTotal));
        contentBox.getChildren().add(buildCharts(r, dolarTotal));
        contentBox.getChildren().add(buildPatrimonioChart(dolarBrl));
        if (!gastosPorMes.isEmpty()) contentBox.getChildren().add(buildGastosChart(gastosPorMes));
        contentBox.getChildren().add(buildExtratoTable(dolarBrl));
        contentBox.getChildren().add(buildGastosTable(gastosPorMes));
        contentBox.getChildren().add(buildAssetsCard(ptaTotal, dolarBrl));
    }

    // ── Pills bar (filter toggles per type) ─────────────────────────────

    private HBox buildPillsBar(ConsolidacaoService.ResultadoConsolidado r, double dolarTotal) {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 4, 0));

        bar.getChildren().addAll(
            makePillToggle(loadIcon("/icons/renda-passiva.png", 14),
                    "Renda Fixa   " + FormatUtil.brl(r.vtarf()),
                    "pill-rf", TipoInvestimento.RENDA_FIXA),
            makePillToggle(null, "💹  Renda Variável   " + FormatUtil.brl(r.vtarv()),
                    "pill-rv", TipoInvestimento.RENDA_VARIAVEL),
            makePillToggle(null, "$  Dólar   " + FormatUtil.brl(dolarTotal),
                    "pill-usd", TipoInvestimento.DOLAR)
        );
        return bar;
    }

    private Button makePillToggle(Node graphic, String text, String colorClass, TipoInvestimento tipo) {
        Button btn = new Button(text);
        if (graphic != null) btn.setGraphic(graphic);
        btn.getStyleClass().addAll("pill-btn", colorClass);
        if (tiposFiltro.contains(tipo)) btn.getStyleClass().add("active");
        btn.setOnAction(e -> {
            if (tiposFiltro.contains(tipo)) {
                if (tiposFiltro.size() > 1) tiposFiltro.remove(tipo);
            } else {
                tiposFiltro.add(tipo);
            }
            refresh();
        });
        return btn;
    }

    // ── 4 KPI cards ─────────────────────────────────────────────────────

    private HBox buildKpiGrid(ConsolidacaoService.ResultadoConsolidado r, double aporteMes, double ptaTotal, double vtiaTotal) {
        HBox row = new HBox(12);

        String gpStyle = r.vtra() >= 0 ? "positive" : "negative";
        String gpColor = r.vtra() >= 0 ? "#3fb950" : "#f85149";

        VBox kpi1 = makeKpiCard("Patrimônio Total", FormatUtil.brl(ptaTotal), "PTA", "neutral", "#bc8cff");
        VBox kpi2 = makeKpiCard("Total Investido", FormatUtil.brl(vtiaTotal), "VTIA", "neutral", "#58a6ff");
        VBox kpi3 = makeKpiCard("Ganho / Perda", FormatUtil.brl(r.vtra()), "VTRA", gpStyle, gpColor);
        String aporteLabel = anoSelecionado == ConsolidacaoService.ANO_TODOS
                ? "Aportado Total" : "Aportado " + anoSelecionado;
        String aporteSub = anoSelecionado == ConsolidacaoService.ANO_TODOS
                ? "Histórico total" : String.valueOf(anoSelecionado);
        VBox kpi4 = makeKpiCard(aporteLabel, FormatUtil.brl(aporteMes), aporteSub, "neutral", "#e3b341");

        for (VBox card : new VBox[]{kpi1, kpi2, kpi3, kpi4}) {
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
        }
        row.getChildren().addAll(kpi1, kpi2, kpi3, kpi4);
        return row;
    }

    private VBox makeKpiCard(String label, String value, String sublabel, String valueStyle, String accent) {
        VBox card = new VBox(6);
        card.getStyleClass().add("kpi-card");
        card.setStyle(String.format(
                "-fx-border-color: #2a3441 #2a3441 #2a3441 %s; -fx-border-width: 1 1 1 3; " +
                "-fx-border-radius: 0 10 10 0; -fx-background-radius: 0 10 10 0;", accent));

        Label lbl = new Label(label);
        lbl.getStyleClass().add("kpi-label");
        GlossarioTooltip.aplicar(lbl, label);

        Label val = new Label(value);
        val.getStyleClass().addAll("kpi-value", valueStyle);

        Label sub = new Label(sublabel);
        sub.getStyleClass().add("card-label");
        GlossarioTooltip.aplicar(sub, sublabel);

        card.getChildren().addAll(lbl, val, sub);
        return card;
    }

    // ── Rendimento card: PCPA + PRAT side by side ───────────────────────

    private HBox buildRendimentoCard(Double pcpa, Double prat, boolean todosOsAnos) {
        HBox row = new HBox(12);
        String labelAno = todosOsAnos ? "no histórico total" : "esse ano";

        if (pcpa != null) {
            String color = pcpa >= 0 ? "#3fb950" : "#f85149";
            VBox card = makeRendCard("Crescimento Patrimonial",
                    (pcpa >= 0 ? "▲ " : "▼ ") + FormatUtil.pct(Math.abs(pcpa)),
                    "Seu patrimônio cresceu " + labelAno, color);
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
            row.getChildren().add(card);
        }

        if (prat != null) {
            String color = prat >= 0 ? "#3fb950" : "#f85149";
            VBox card = makeRendCard("Rendimento sobre Investido",
                    (prat >= 0 ? "▲ " : "▼ ") + FormatUtil.pct(Math.abs(prat)),
                    "Rendimento sobre o capital aplicado " + labelAno, color);
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
            row.getChildren().add(card);
        }

        return row;
    }

    private VBox makeRendCard(String label, String pctValue, String subtext, String color) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");

        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-title");
        GlossarioTooltip.aplicar(lbl, label);

        Label val = new Label(pctValue);
        val.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label sub = new Label(subtext);
        sub.getStyleClass().add("card-label");

        card.getChildren().addAll(lbl, val, sub);
        return card;
    }

    // ── Charts ──────────────────────────────────────────────────────────

    private HBox buildCharts(ConsolidacaoService.ResultadoConsolidado r, double dolarTotal) {
        HBox hbox = new HBox(12);

        java.awt.Color bgCard   = new java.awt.Color(0x16, 0x1b, 0x22);
        java.awt.Color border   = new java.awt.Color(0x2a, 0x34, 0x41);
        java.awt.Color textMut  = new java.awt.Color(0x7d, 0x8f, 0xa0);
        java.awt.Color textMain = new java.awt.Color(0xe6, 0xed, 0xf3);
        java.awt.Color green    = new java.awt.Color(0x3f, 0xb9, 0x50);
        java.awt.Color red      = new java.awt.Color(0xf8, 0x51, 0x49);
        java.awt.Color blue     = new java.awt.Color(0x58, 0xa6, 0xff);
        java.awt.Color purple   = new java.awt.Color(0xbc, 0x8c, 0xff);
        java.awt.Color amber    = new java.awt.Color(0xe3, 0xb3, 0x41);
        java.awt.Font  fontSm   = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11);
        java.awt.Font  fontXs   = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        java.awt.Font  fontBold = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);
        java.awt.BasicStroke lineStroke = new java.awt.BasicStroke(
                1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND);
        java.awt.geom.Ellipse2D.Double dotShape = new java.awt.geom.Ellipse2D.Double(-5, -5, 10, 10);

        // ── Pie chart ─────────────────────────────────────────────────
        DefaultPieDataset<String> pieData = new DefaultPieDataset<>();
        if (r.vtarf() > 0) pieData.setValue("Renda Fixa", r.vtarf());
        if (r.vtarv() > 0) pieData.setValue("Renda Variável", r.vtarv());
        if (dolarTotal > 0) pieData.setValue("Dólar", dolarTotal);

        JFreeChart pie = ChartFactory.createPieChart("Alocação", pieData, true, false, false);
        pie.setBackgroundPaint(bgCard);
        pie.setBorderVisible(false);
        pie.getTitle().setPaint(textMut);
        pie.getTitle().setFont(fontBold);

        org.jfree.chart.plot.PiePlot<?> piePlot = (org.jfree.chart.plot.PiePlot<?>) pie.getPlot();
        piePlot.setBackgroundPaint(bgCard);
        piePlot.setOutlinePaint(null);
        piePlot.setShadowPaint(null);
        piePlot.setSectionPaint("Renda Fixa", green);
        piePlot.setSectionPaint("Renda Variável", blue);
        piePlot.setSectionPaint("Dólar", amber);
        piePlot.setLabelBackgroundPaint(new java.awt.Color(0x1c, 0x22, 0x30));
        piePlot.setLabelOutlinePaint(border);
        piePlot.setLabelShadowPaint(null);
        piePlot.setLabelFont(fontXs);
        piePlot.setLabelPaint(textMain);

        org.jfree.chart.title.LegendTitle pieLegend = pie.getLegend();
        if (pieLegend != null) {
            pieLegend.setBackgroundPaint(bgCard);
            pieLegend.setItemPaint(textMut);
            pieLegend.setItemFont(fontSm);
        }

        SwingNode pieNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(pie);
            cp.setBackground(bgCard);
            pieNode.setContent(cp);
        });
        StackPane pieWrapper = new StackPane(pieNode);
        pieWrapper.setPrefSize(300, 240);

        // ── Rendimento Mensal ──────────────────────────────────────────
        DefaultCategoryDataset monthlyData = buildMonthlyData();

        org.jfree.chart.axis.CategoryAxis xAxisM = new org.jfree.chart.axis.CategoryAxis(null);
        xAxisM.setTickLabelPaint(textMut);
        xAxisM.setTickLabelFont(fontXs);
        xAxisM.setAxisLinePaint(border);
        xAxisM.setTickMarkPaint(border);

        org.jfree.chart.axis.NumberAxis yAxisM = new org.jfree.chart.axis.NumberAxis("R$");
        yAxisM.setTickLabelPaint(textMut);
        yAxisM.setTickLabelFont(fontXs);
        yAxisM.setAxisLinePaint(border);
        yAxisM.setTickMarkPaint(border);
        yAxisM.setLabelPaint(textMut);
        yAxisM.setLabelFont(fontSm);

        org.jfree.chart.renderer.category.LineAndShapeRenderer monthlyRenderer =
            new org.jfree.chart.renderer.category.LineAndShapeRenderer() {
                @Override public java.awt.Paint getItemPaint(int row, int col) { return textMut; }
                @Override public java.awt.Paint getItemFillPaint(int row, int col) {
                    Number val = monthlyData.getValue(row, col);
                    return (val != null && val.doubleValue() >= 0) ? green : red;
                }
            };
        monthlyRenderer.setDefaultLinesVisible(true);
        monthlyRenderer.setDefaultShapesVisible(true);
        monthlyRenderer.setDefaultShapesFilled(true);
        monthlyRenderer.setUseFillPaint(true);
        monthlyRenderer.setDefaultShape(dotShape);
        monthlyRenderer.setDefaultStroke(lineStroke);

        org.jfree.chart.plot.CategoryPlot monthlyPlot = new org.jfree.chart.plot.CategoryPlot(
                monthlyData, xAxisM, yAxisM, monthlyRenderer);
        monthlyPlot.setBackgroundPaint(bgCard);
        monthlyPlot.setOutlinePaint(border);
        monthlyPlot.setRangeGridlinePaint(border);
        monthlyPlot.setDomainGridlinesVisible(false);
        monthlyPlot.setRangeZeroBaselinePaint(textMut);
        monthlyPlot.setRangeZeroBaselineVisible(true);

        JFreeChart monthlyChart = new JFreeChart("Rendimento Mensal", fontBold, monthlyPlot, false);
        monthlyChart.setBackgroundPaint(bgCard);
        monthlyChart.setBorderVisible(false);
        monthlyChart.getTitle().setPaint(textMut);
        monthlyChart.getTitle().setFont(fontBold);

        SwingNode monthlyNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(monthlyChart);
            cp.setBackground(bgCard);
            monthlyNode.setContent(cp);
        });
        StackPane monthlyWrapper = new StackPane(monthlyNode);
        monthlyWrapper.setPrefSize(360, 240);

        // ── Rendimento Acumulado ───────────────────────────────────────
        DefaultCategoryDataset accData = buildAccumulatedData();

        org.jfree.chart.axis.CategoryAxis xAxisA = new org.jfree.chart.axis.CategoryAxis(null);
        xAxisA.setTickLabelPaint(textMut);
        xAxisA.setTickLabelFont(fontXs);
        xAxisA.setAxisLinePaint(border);
        xAxisA.setTickMarkPaint(border);

        org.jfree.chart.axis.NumberAxis yAxisA = new org.jfree.chart.axis.NumberAxis("R$");
        yAxisA.setTickLabelPaint(textMut);
        yAxisA.setTickLabelFont(fontXs);
        yAxisA.setAxisLinePaint(border);
        yAxisA.setTickMarkPaint(border);
        yAxisA.setLabelPaint(textMut);
        yAxisA.setLabelFont(fontSm);

        org.jfree.chart.renderer.category.LineAndShapeRenderer accRenderer =
            new org.jfree.chart.renderer.category.LineAndShapeRenderer() {
                @Override public java.awt.Paint getItemPaint(int row, int col) { return purple; }
                @Override public java.awt.Paint getItemFillPaint(int row, int col) {
                    Number val = accData.getValue(row, col);
                    return (val != null && val.doubleValue() >= 0) ? purple : red;
                }
            };
        accRenderer.setDefaultLinesVisible(true);
        accRenderer.setDefaultShapesVisible(true);
        accRenderer.setDefaultShapesFilled(true);
        accRenderer.setUseFillPaint(true);
        accRenderer.setDefaultShape(dotShape);
        accRenderer.setDefaultStroke(lineStroke);

        org.jfree.chart.plot.CategoryPlot accPlot = new org.jfree.chart.plot.CategoryPlot(
                accData, xAxisA, yAxisA, accRenderer);
        accPlot.setBackgroundPaint(bgCard);
        accPlot.setOutlinePaint(border);
        accPlot.setRangeGridlinePaint(border);
        accPlot.setDomainGridlinesVisible(false);
        accPlot.setRangeZeroBaselinePaint(textMut);
        accPlot.setRangeZeroBaselineVisible(true);

        JFreeChart accChart = new JFreeChart("Rendimento Acumulado", fontBold, accPlot, false);
        accChart.setBackgroundPaint(bgCard);
        accChart.setBorderVisible(false);
        accChart.getTitle().setPaint(textMut);
        accChart.getTitle().setFont(fontBold);

        SwingNode accNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(accChart);
            cp.setBackground(bgCard);
            accNode.setContent(cp);
        });
        StackPane accWrapper = new StackPane(accNode);
        accWrapper.setPrefSize(360, 240);

        VBox pieBox = new VBox(pieWrapper);
        pieBox.getStyleClass().add("card");
        pieBox.setPadding(Insets.EMPTY);
        HBox.setHgrow(pieBox, Priority.NEVER);

        VBox monthlyBox = new VBox(monthlyWrapper);
        monthlyBox.getStyleClass().add("card");
        monthlyBox.setPadding(Insets.EMPTY);
        HBox.setHgrow(monthlyBox, Priority.ALWAYS);

        VBox accBox = new VBox(accWrapper);
        accBox.getStyleClass().add("card");
        accBox.setPadding(Insets.EMPTY);
        HBox.setHgrow(accBox, Priority.ALWAYS);

        hbox.getChildren().addAll(pieBox, monthlyBox, accBox);
        return hbox;
    }

    private DefaultCategoryDataset buildMonthlyData() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ConsolidacaoService.VtraMensal m : consolSvc.calcularVtraPorMes(anoSelecionado, tiposFiltro)) {
            ds.addValue(m.vtra(), "Rendimento", FormatUtil.mesAno(m.mes(), m.ano()));
        }
        return ds;
    }

    private DefaultCategoryDataset buildAccumulatedData() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        double acumulado = 0;
        for (ConsolidacaoService.VtraMensal m : consolSvc.calcularVtraPorMes(anoSelecionado, tiposFiltro)) {
            acumulado += m.vtra();
            ds.addValue(acumulado, "Acumulado", FormatUtil.mesAno(m.mes(), m.ano()));
        }
        return ds;
    }

    // ── Mini type cards ──────────────────────────────────────────────────

    private HBox buildMiniTypeCards(ConsolidacaoService.ResultadoConsolidado r, double dolarTotal) {
        HBox row = new HBox(12);

        if (tiposFiltro.contains(TipoInvestimento.RENDA_FIXA)) {
            VBox card = makeMiniCard("🏦", "Renda Fixa", FormatUtil.brl(r.viarf()),
                    "Rendimentos: " + FormatUtil.brl(r.rarf()), "badge-rf");
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
            row.getChildren().add(card);
        }

        if (tiposFiltro.contains(TipoInvestimento.RENDA_VARIAVEL)) {
            VBox card = makeMiniCard("💹", "Renda Variável", FormatUtil.brl(r.parv()),
                    "Dividendos: " + FormatUtil.brl(r.dta()), "badge-rv");
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
            row.getChildren().add(card);
        }

        if (tiposFiltro.contains(TipoInvestimento.DOLAR)) {
            VBox card = makeMiniCard("💵", "Dólar", FormatUtil.brl(dolarTotal),
                    "Valor em reais pela cotação atual", "badge-usd");
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
            row.getChildren().add(card);
        }

        return row;
    }

    private VBox makeMiniCard(String icon, String label, String total, String subtext, String badgeStyle) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        HBox hdr = new HBox(8);
        hdr.setAlignment(Pos.CENTER_LEFT);
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 20px;");
        Label typeLbl = new Label(label);
        typeLbl.getStyleClass().add(badgeStyle);
        GlossarioTooltip.aplicar(typeLbl, label);
        hdr.getChildren().addAll(iconLbl, typeLbl);

        Label totalLbl = new Label(total);
        totalLbl.getStyleClass().add("card-value-sm");

        Label subLbl = new Label(subtext);
        subLbl.getStyleClass().add("card-label");

        card.getChildren().addAll(hdr, totalLbl, subLbl);
        return card;
    }

    // ── Assets table ────────────────────────────────────────────────────

    private VBox buildAssetsCard(double pta, double dolarBrl) {
        TableView<Investimento> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Investimento, String> colNome = new TableColumn<>("Ativo");
        colNome.setMinWidth(colW("Ativo", 0));
        colNome.setPrefWidth(200);
        colNome.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNome()));

        TableColumn<Investimento, String> colTipo = new TableColumn<>("Tipo");
        colTipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTipo().label()));
        double tipoW = colW("Tipo", 120);
        colTipo.setMinWidth(tipoW);
        colTipo.setPrefWidth(tipoW);

        TableColumn<Investimento, String> colSaldo = new TableColumn<>("Saldo Atual");
        colSaldo.setCellValueFactory(c -> {
            Investimento inv = c.getValue();
            double saldo = switch (inv.getTipo()) {
                case RENDA_FIXA -> saldoSvc.saldoAtual(inv, 0);
                case RENDA_VARIAVEL -> { var pos = rvSvc.calcular(inv.getId()); yield pos.vac() * pos.qc(); }
                case DOLAR -> consolSvc.calcularSaldoUsd(inv.getId()) * dolarBrl;
            };
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(saldo));
        });
        double saldoW = colW("Saldo Atual", 140);
        colSaldo.setMinWidth(saldoW);
        colSaldo.setPrefWidth(saldoW);

        TableColumn<Investimento, String> colPct = new TableColumn<>("% Carteira");
        colPct.setCellValueFactory(c -> {
            Investimento inv = c.getValue();
            double saldo = switch (inv.getTipo()) {
                case RENDA_FIXA -> saldoSvc.saldoAtual(inv, 0);
                case RENDA_VARIAVEL -> { var pos = rvSvc.calcular(inv.getId()); yield pos.vac() * pos.qc(); }
                case DOLAR -> consolSvc.calcularSaldoUsd(inv.getId()) * dolarBrl;
            };
            double pct = pta > 0 ? saldo / pta * 100 : 0;
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.pct(pct));
        });
        double pctW = colW("% Carteira", 100);
        colPct.setMinWidth(pctW);
        colPct.setPrefWidth(pctW);

        table.getColumns().addAll(colNome, colTipo, colSaldo, colPct);

        table.getItems().addAll(invRepo.findAll().stream()
                .filter(inv -> tiposFiltro.contains(inv.getTipo()))
                .collect(Collectors.toList()));

        int rowH = 32, headerH = 30;
        table.setPrefHeight(table.getItems().size() * rowH + headerH);
        table.setMinHeight(table.getPrefHeight());

        Label titleLbl = new Label("Ativos da Carteira");
        titleLbl.getStyleClass().add("section-title");
        VBox box = new VBox(8, titleLbl, table);
        box.getStyleClass().add("card");
        return box;
    }

    // ── Histórico mensal (tabela) ────────────────────────────────────────

    private VBox buildExtratoTable(double dolarBrl) {
        List<ConsolidacaoService.ExtratoMensal> dados =
                consolSvc.calcularExtratoPorMes(anoSelecionado, tiposFiltro, dolarBrl);

        TableView<ConsolidacaoService.ExtratoMensal> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Sem dados para o período selecionado"));

        TableColumn<ConsolidacaoService.ExtratoMensal, String> colData = new TableColumn<>("Data");
        colData.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().mes(), c.getValue().ano())));
        colData.setMinWidth(colW("Data", 90));
        colData.setPrefWidth(100);

        TableColumn<ConsolidacaoService.ExtratoMensal, String> colAplicado = new TableColumn<>("Valor Aplicado");
        colAplicado.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().valorAplicado())));
        colAplicado.setMinWidth(colW("Valor Aplicado", 130));

        TableColumn<ConsolidacaoService.ExtratoMensal, String> colRend = new TableColumn<>("Rendimentos");
        colRend.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().valorRendimento())));
        colRend.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                int idx = getIndex();
                if (idx >= 0 && idx < getTableView().getItems().size()) {
                    double v = getTableView().getItems().get(idx).valorRendimento();
                    setStyle("-fx-text-fill: " + (v >= 0 ? "#3fb950" : "#f85149") + ";");
                }
            }
        });
        colRend.setMinWidth(colW("Rendimentos", 130));

        TableColumn<ConsolidacaoService.ExtratoMensal, String> colTotal = new TableColumn<>("Valor Total");
        colTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().valorTotal())));
        colTotal.setMinWidth(colW("Valor Total", 130));

        table.getColumns().addAll(colData, colAplicado, colRend, colTotal);
        table.getItems().addAll(dados);

        int rowH = 32, headerH = 30;
        if (anoSelecionado == ConsolidacaoService.ANO_TODOS) {
            table.setPrefHeight(10 * rowH + headerH);
        } else {
            table.setPrefHeight(12 * rowH + headerH);
            table.setMinHeight(12 * rowH + headerH);
        }

        Label titleLbl = new Label("Histórico Mensal");
        titleLbl.getStyleClass().add("section-title");
        VBox box = new VBox(8, titleLbl, table);
        box.getStyleClass().add("card");
        return box;
    }

    // ── Patrimônio por mês (scatter/linha) ──────────────────────────────

    private VBox buildPatrimonioChart(double dolarBrl) {
        java.awt.Color bgCard   = new java.awt.Color(0x16, 0x1b, 0x22);
        java.awt.Color border   = new java.awt.Color(0x2a, 0x34, 0x41);
        java.awt.Color textMut  = new java.awt.Color(0x7d, 0x8f, 0xa0);
        java.awt.Color purple   = new java.awt.Color(0xbc, 0x8c, 0xff);
        java.awt.Font  fontSm   = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11);
        java.awt.Font  fontXs   = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        java.awt.Font  fontBold = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);
        java.awt.BasicStroke lineStroke = new java.awt.BasicStroke(
                1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND);
        java.awt.geom.Ellipse2D.Double dotShape = new java.awt.geom.Ellipse2D.Double(-6, -6, 12, 12);

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ConsolidacaoService.PatrimonioMensal p :
                consolSvc.calcularPtaPorMes(anoSelecionado, tiposFiltro, dolarBrl)) {
            ds.addValue(p.pta(), "Patrimônio", FormatUtil.mesAno(p.mes(), p.ano()));
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
        yAxis.setLabelFont(fontSm);
        yAxis.setAutoRangeIncludesZero(false);

        org.jfree.chart.renderer.category.LineAndShapeRenderer renderer =
            new org.jfree.chart.renderer.category.LineAndShapeRenderer() {
                @Override public java.awt.Paint getItemPaint(int row, int col) { return purple; }
                @Override public java.awt.Paint getItemFillPaint(int row, int col) { return purple; }
            };
        renderer.setDefaultLinesVisible(true);
        renderer.setDefaultShapesVisible(true);
        renderer.setDefaultShapesFilled(true);
        renderer.setUseFillPaint(true);
        renderer.setDefaultShape(dotShape);
        renderer.setDefaultStroke(lineStroke);

        org.jfree.chart.plot.CategoryPlot plot = new org.jfree.chart.plot.CategoryPlot(
                ds, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(bgCard);
        plot.setOutlinePaint(border);
        plot.setRangeGridlinePaint(border);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeZeroBaselineVisible(false);

        JFreeChart chart = new JFreeChart("Patrimônio por Mês", fontBold, plot, false);
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
        wrapper.setPrefHeight(200);

        VBox box = new VBox(wrapper);
        box.getStyleClass().add("card");
        box.setPadding(Insets.EMPTY);
        return box;
    }

    // ── Gastos KPI row ───────────────────────────────────────────────────

    private HBox buildGastosKpiRow(double gt, double gat, double gdt, double gmt) {
        VBox kGT  = makeKpiCard("Gastos Totais", FormatUtil.brl(gt),  "GT",  "neutral", "#f0883e");
        VBox kGAT = makeKpiCard("Alimentar",     FormatUtil.brl(gat), "GAT", "neutral", "#e3b341");
        VBox kGDT = makeKpiCard("Diversos",      FormatUtil.brl(gdt), "GDT", "neutral", "#58a6ff");
        VBox kGMT = makeKpiCard("Mensalidades",  FormatUtil.brl(gmt), "GMT", "neutral", "#bc8cff");
        HBox row = new HBox(12, kGT, kGAT, kGDT, kGMT);
        for (VBox c : new VBox[]{kGT, kGAT, kGDT, kGMT}) {
            c.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(c, Priority.ALWAYS);
        }
        return row;
    }

    private VBox buildGastosChart(List<ResumoMensal> dados) {
        java.awt.Color bgCard  = new java.awt.Color(0x16, 0x1b, 0x22);
        java.awt.Color border  = new java.awt.Color(0x2a, 0x34, 0x41);
        java.awt.Color textMut = new java.awt.Color(0x7d, 0x8f, 0xa0);
        java.awt.Color amber   = new java.awt.Color(0xe3, 0xb3, 0x41);
        java.awt.Color blue    = new java.awt.Color(0x58, 0xa6, 0xff);
        java.awt.Color purple  = new java.awt.Color(0xbc, 0x8c, 0xff);
        java.awt.Color orange  = new java.awt.Color(0xf0, 0x88, 0x3e);
        java.awt.Font  fontXs  = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        java.awt.Font  fontBold= new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);
        java.awt.BasicStroke stroke = new java.awt.BasicStroke(
                1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND);
        java.awt.geom.Ellipse2D.Double dot = new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8);

        java.awt.Color[] colors = {amber, blue, purple, orange};

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ResumoMensal r : dados) {
            String lbl = FormatUtil.mesAno(r.mes(), r.ano());
            ds.addValue(r.alimentar(),   "Alimentar",    lbl);
            ds.addValue(r.diverso(),     "Diversos",     lbl);
            ds.addValue(r.mensalidade(), "Mensalidades", lbl);
            ds.addValue(r.total(),       "Total",        lbl);
        }

        org.jfree.chart.axis.CategoryAxis xAxis = new org.jfree.chart.axis.CategoryAxis(null);
        xAxis.setTickLabelPaint(textMut); xAxis.setTickLabelFont(fontXs);
        xAxis.setAxisLinePaint(border);   xAxis.setTickMarkPaint(border);

        org.jfree.chart.axis.NumberAxis yAxis = new org.jfree.chart.axis.NumberAxis("R$");
        yAxis.setTickLabelPaint(textMut); yAxis.setTickLabelFont(fontXs);
        yAxis.setAxisLinePaint(border);   yAxis.setTickMarkPaint(border);
        yAxis.setLabelPaint(textMut);
        yAxis.setLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        yAxis.setAutoRangeIncludesZero(true);

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
        // linha do Total um pouco mais grossa para destacar
        renderer.setSeriesStroke(3, new java.awt.BasicStroke(
                2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

        org.jfree.chart.plot.CategoryPlot plot = new org.jfree.chart.plot.CategoryPlot(ds, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(bgCard);
        plot.setOutlinePaint(border);
        plot.setRangeGridlinePaint(border);
        plot.setDomainGridlinesVisible(false);

        JFreeChart chart = new JFreeChart("Gastos por Mês", fontBold, plot, true);
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
        wrapper.setPrefHeight(240);

        VBox box = new VBox(wrapper);
        box.getStyleClass().add("card");
        box.setPadding(Insets.EMPTY);
        return box;
    }

    private VBox buildGastosTable(List<ResumoMensal> dados) {
        TableView<ResumoMensal> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Sem gastos no período selecionado."));

        TableColumn<ResumoMensal, String> cData = new TableColumn<>("Data");
        cData.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().mes(), c.getValue().ano())));
        cData.setPrefWidth(colW("Data", 90));

        TableColumn<ResumoMensal, String> cAli = new TableColumn<>("Alimentar");
        cAli.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().alimentar())));
        cAli.setStyle("-fx-alignment: CENTER-RIGHT;");
        cAli.setPrefWidth(colW("Alimentar", 120));

        TableColumn<ResumoMensal, String> cDiv = new TableColumn<>("Diversos");
        cDiv.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().diverso())));
        cDiv.setStyle("-fx-alignment: CENTER-RIGHT;");
        cDiv.setPrefWidth(colW("Diversos", 120));

        TableColumn<ResumoMensal, String> cMen = new TableColumn<>("Mensalidade");
        cMen.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().mensalidade())));
        cMen.setStyle("-fx-alignment: CENTER-RIGHT;");
        cMen.setPrefWidth(colW("Mensalidade", 120));

        TableColumn<ResumoMensal, String> cTot = new TableColumn<>("Total");
        cTot.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().total())));
        cTot.setStyle("-fx-alignment: CENTER-RIGHT;");
        cTot.setPrefWidth(colW("Total", 120));
        cTot.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
            }
        });

        table.getColumns().addAll(cData, cAli, cDiv, cMen, cTot);
        table.getItems().addAll(dados);

        int rowH = 32, headerH = 30;
        if (anoSelecionado == ConsolidacaoService.ANO_TODOS) {
            table.setPrefHeight(10 * rowH + headerH);
        } else {
            table.setPrefHeight(12 * rowH + headerH);
            table.setMinHeight(12 * rowH + headerH);
        }

        Label titleLbl = new Label("Gastos por Mês");
        titleLbl.getStyleClass().add("section-title");
        VBox box = new VBox(8, titleLbl, table);
        box.getStyleClass().add("card");
        return box;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void atualizarAlertas() {
        alertBox.getChildren().clear();
        for (String a : alertaSvc.alertasPendentes()) {
            Label lbl = new Label(a);
            lbl.getStyleClass().add("alert-bar");
            lbl.setMaxWidth(Double.MAX_VALUE);
            alertBox.getChildren().add(lbl);
        }
    }

    private void atualizarUsd() {
        cotacaoSvc.getCotacaoAtual().ifPresentOrElse(
                c -> Platform.runLater(() -> usdLabel.setText("💰 USD: R$"
                        + FormatUtil.numero(c.getValorCompra(), 2) + "/" + FormatUtil.numero(c.getValorVenda(), 2))),
                () -> usdLabel.setText("💰 USD: —"));
    }

    private ImageView loadIcon(String resourcePath, int size) {
        return UIUtil.loadTintedIcon(getClass(), resourcePath, size);
    }

    private static double colW(String header, double contentMin) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(header);
        t.setFont(javafx.scene.text.Font.font("SansSerif", javafx.scene.text.FontWeight.BOLD, 11));
        return Math.max(contentMin, Math.ceil(t.getBoundsInLocal().getWidth()) + 32);
    }
}
