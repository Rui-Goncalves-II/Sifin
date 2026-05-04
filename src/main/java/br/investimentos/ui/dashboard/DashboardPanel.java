package br.investimentos.ui.dashboard;

import br.investimentos.model.CotacaoDolar;
import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.ui.util.FormatUtil;
import br.investimentos.ui.util.GlossarioTooltip;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
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
import java.util.List;
import java.util.function.Consumer;

public class DashboardPanel extends BorderPane {

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final AporteRvRepository aporteRepo;
    private final VtaMensalRepository vtaRepo;
    private final VacMensalRepository vacRepo;
    private final RendimentoService rendSvc;
    private final RendaVariavelService rvSvc;
    private final SaldoService saldoSvc;
    private final ConsolidacaoService consolSvc;
    private final CotacaoService cotacaoSvc;
    private final AlertaService alertaSvc;
    private final Consumer<Node> navigate;

    private Label usdLabel;
    private HBox yearBar;
    private VBox alertBox;
    private VBox contentBox;
    private int anoSelecionado;

    public DashboardPanel(InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
                          AporteRvRepository aporteRepo, VtaMensalRepository vtaRepo,
                          VacMensalRepository vacRepo, RendimentoService rendSvc,
                          RendaVariavelService rvSvc, SaldoService saldoSvc,
                          ConsolidacaoService consolSvc, CotacaoService cotacaoSvc,
                          AlertaService alertaSvc, Consumer<Node> navigate) {
        this.invRepo = invRepo; this.movRepo = movRepo; this.aporteRepo = aporteRepo;
        this.vtaRepo = vtaRepo; this.vacRepo = vacRepo; this.rendSvc = rendSvc;
        this.rvSvc = rvSvc; this.saldoSvc = saldoSvc; this.consolSvc = consolSvc;
        this.cotacaoSvc = cotacaoSvc; this.alertaSvc = alertaSvc; this.navigate = navigate;

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

        yearBar = new HBox(6);
        yearBar.setAlignment(Pos.CENTER_LEFT);
        buildYearBar();

        header.getChildren().addAll(title, spacer, yearBar, usdLabel);
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

    private void buildYearBar() {
        yearBar.getChildren().clear();
        List<Integer> anos = consolSvc.anosComDados();
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
        if (anoSelecionado == ConsolidacaoService.ANO_TODOS) todos.getStyleClass().add("active");
        todos.setOnAction(e -> { anoSelecionado = ConsolidacaoService.ANO_TODOS; buildYearBar(); refresh(); });
        yearBar.getChildren().add(todos);
    }

    private void refresh() {
        ConsolidacaoService.ResultadoConsolidado r = consolSvc.calcular(anoSelecionado);
        double dolarBrl = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorCompra).orElse(0.0);
        double dolarTotal = calcDolarTotal(dolarBrl);
        LocalDate hoje = LocalDate.now();
        double aporteMes = consolSvc.calcularAportesDoMes(hoje.getMonthValue(), hoje.getYear());

        contentBox.getChildren().clear();

        atualizarAlertas();
        contentBox.getChildren().add(alertBox);
        contentBox.getChildren().add(buildPillsBar(r, dolarTotal));
        contentBox.getChildren().add(buildKpiGrid(r, aporteMes, hoje));

        HBox rendRow = buildRendimentoCard(r);
        if (!rendRow.getChildren().isEmpty()) contentBox.getChildren().add(rendRow);

        contentBox.getChildren().add(buildCharts(r, dolarTotal));
        contentBox.getChildren().add(buildMiniTypeCards(r, dolarTotal));
        contentBox.getChildren().add(buildAssetsCard(r.pta()));
    }

    // ── Pills bar (display-only totals per type) ────────────────────────

    private HBox buildPillsBar(ConsolidacaoService.ResultadoConsolidado r, double dolarTotal) {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 4, 0));

        Label rfPill = new Label("🏦  Renda Fixa   " + FormatUtil.brlAbrev(r.vtarf()));
        rfPill.getStyleClass().addAll("pill-btn", "pill-rf", "active");

        Label rvPill = new Label("💹  Renda Variável   " + FormatUtil.brlAbrev(r.vtarv()));
        rvPill.getStyleClass().addAll("pill-btn", "pill-rv", "active");

        Label usdPill = new Label("💵  Dólar   " + FormatUtil.brlAbrev(dolarTotal));
        usdPill.getStyleClass().addAll("pill-btn", "pill-usd", "active");

        bar.getChildren().addAll(rfPill, rvPill, usdPill);
        return bar;
    }

    // ── 4 KPI cards ─────────────────────────────────────────────────────

    private HBox buildKpiGrid(ConsolidacaoService.ResultadoConsolidado r, double aporteMes, LocalDate hoje) {
        HBox row = new HBox(12);

        String gpStyle = r.vtra() >= 0 ? "positive" : "negative";
        String gpColor = r.vtra() >= 0 ? "#3fb950" : "#f85149";

        VBox kpi1 = makeKpiCard("Patrimônio Total", FormatUtil.brl(r.pta()), "PTA", "neutral", "#bc8cff");
        VBox kpi2 = makeKpiCard("Total Investido", FormatUtil.brl(r.vtia()), "VTIA", "neutral", "#58a6ff");
        VBox kpi3 = makeKpiCard("Ganho / Perda", FormatUtil.brl(r.vtra()), "VTRA", gpStyle, gpColor);
        VBox kpi4 = makeKpiCard("Aporte do Mês", FormatUtil.brl(aporteMes),
                FormatUtil.mesAno(hoje.getMonthValue(), hoje.getYear()), "neutral", "#e3b341");

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

        Label val = new Label(value);
        val.getStyleClass().addAll("kpi-value", valueStyle);

        Label sub = new Label(sublabel);
        sub.getStyleClass().add("card-label");
        GlossarioTooltip.aplicar(sub, sublabel);

        card.getChildren().addAll(lbl, val, sub);
        return card;
    }

    // ── Rendimento card: PCPA + PRAT side by side ───────────────────────

    private HBox buildRendimentoCard(ConsolidacaoService.ResultadoConsolidado r) {
        HBox row = new HBox(12);
        String labelAno = r.todosOsAnos() ? "no histórico total" : "esse ano";

        if (r.pcpa() != null) {
            String color = r.pcpa() >= 0 ? "#3fb950" : "#f85149";
            VBox card = makeRendCard("Crescimento Patrimonial",
                    (r.pcpa() >= 0 ? "▲ " : "▼ ") + FormatUtil.pct(Math.abs(r.pcpa())),
                    "Seu patrimônio cresceu " + labelAno, color);
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
            row.getChildren().add(card);
        }

        if (r.prat() != null) {
            String color = r.prat() >= 0 ? "#3fb950" : "#f85149";
            VBox card = makeRendCard("Rendimento sobre Investido",
                    (r.prat() >= 0 ? "▲ " : "▼ ") + FormatUtil.pct(Math.abs(r.prat())),
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
        val.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

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
        java.awt.Color amber    = new java.awt.Color(0xe3, 0xb3, 0x41);
        java.awt.Font  fontSm   = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11);
        java.awt.Font  fontXs   = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        java.awt.Font  fontBold = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);

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
        pieWrapper.setPrefSize(340, 240);

        // ── Dot chart (retorno mensal) ─────────────────────────────────
        DefaultCategoryDataset dotData = buildMonthlyData();

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

        org.jfree.chart.renderer.category.LineAndShapeRenderer dotRenderer =
            new org.jfree.chart.renderer.category.LineAndShapeRenderer() {
                @Override
                public java.awt.Paint getItemPaint(int row, int column) {
                    Number val = dotData.getValue(row, column);
                    return (val != null && val.doubleValue() >= 0) ? green : red;
                }
            };
        dotRenderer.setDefaultLinesVisible(false);
        dotRenderer.setDefaultShapesVisible(true);
        dotRenderer.setDefaultShapesFilled(true);
        dotRenderer.setDefaultShape(new java.awt.geom.Ellipse2D.Double(-5, -5, 10, 10));

        org.jfree.chart.plot.CategoryPlot dotPlot = new org.jfree.chart.plot.CategoryPlot(
                dotData, xAxis, yAxis, dotRenderer);
        dotPlot.setBackgroundPaint(bgCard);
        dotPlot.setOutlinePaint(border);
        dotPlot.setRangeGridlinePaint(border);
        dotPlot.setDomainGridlinesVisible(false);
        dotPlot.setRangeZeroBaselinePaint(textMut);
        dotPlot.setRangeZeroBaselineVisible(true);

        JFreeChart dot = new JFreeChart("Retorno Mensal", fontBold, dotPlot, false);
        dot.setBackgroundPaint(bgCard);
        dot.setBorderVisible(false);
        dot.getTitle().setPaint(textMut);
        dot.getTitle().setFont(fontBold);

        SwingNode barNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(dot);
            cp.setBackground(bgCard);
            barNode.setContent(cp);
        });
        StackPane barWrapper = new StackPane(barNode);
        barWrapper.setPrefSize(460, 240);

        VBox pieBox = new VBox(pieWrapper);
        pieBox.getStyleClass().add("card");
        pieBox.setPadding(Insets.EMPTY);
        HBox.setHgrow(pieBox, Priority.NEVER);

        VBox barBox = new VBox(barWrapper);
        barBox.getStyleClass().add("card");
        barBox.setPadding(Insets.EMPTY);
        HBox.setHgrow(barBox, Priority.ALWAYS);

        hbox.getChildren().addAll(pieBox, barBox);
        return hbox;
    }

    private DefaultCategoryDataset buildMonthlyData() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ConsolidacaoService.VtraMensal m : consolSvc.calcularVtraPorMes(anoSelecionado)) {
            ds.addValue(m.vtra(), "Retorno", FormatUtil.mesAno(m.mes(), m.ano()));
        }
        return ds;
    }

    // ── Mini type cards ──────────────────────────────────────────────────

    private HBox buildMiniTypeCards(ConsolidacaoService.ResultadoConsolidado r, double dolarTotal) {
        HBox row = new HBox(12);

        VBox rfCard = makeMiniCard("🏦", "Renda Fixa", FormatUtil.brl(r.vtarf()),
                "Rendimentos: " + FormatUtil.brl(r.rarf()), "badge-rf");
        VBox rvCard = makeMiniCard("💹", "Renda Variável", FormatUtil.brl(r.vtarv()),
                "Dividendos: " + FormatUtil.brl(r.dta()), "badge-rv");
        VBox usdCard = makeMiniCard("💵", "Dólar", FormatUtil.brl(dolarTotal),
                "Valor em reais pela cotação atual", "badge-usd");

        for (VBox card : new VBox[]{rfCard, rvCard, usdCard}) {
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
        }
        row.getChildren().addAll(rfCard, rvCard, usdCard);
        return row;
    }

    private VBox makeMiniCard(String icon, String label, String total, String subtext, String badgeStyle) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        HBox hdr = new HBox(8);
        hdr.setAlignment(Pos.CENTER_LEFT);
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 16px;");
        Label typeLbl = new Label(label);
        typeLbl.getStyleClass().add(badgeStyle);
        hdr.getChildren().addAll(iconLbl, typeLbl);

        Label totalLbl = new Label(total);
        totalLbl.getStyleClass().add("card-value-sm");

        Label subLbl = new Label(subtext);
        subLbl.getStyleClass().add("card-label");

        card.getChildren().addAll(hdr, totalLbl, subLbl);
        return card;
    }

    // ── Assets table ────────────────────────────────────────────────────

    private VBox buildAssetsCard(double pta) {
        TableView<Investimento> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setPrefHeight(220);

        TableColumn<Investimento, String> colNome = new TableColumn<>("Ativo");
        colNome.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNome()));
        colNome.setPrefWidth(200);

        TableColumn<Investimento, String> colTipo = new TableColumn<>("Tipo");
        colTipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTipo().label()));
        colTipo.setPrefWidth(120);

        TableColumn<Investimento, String> colSaldo = new TableColumn<>("Saldo Atual");
        colSaldo.setCellValueFactory(c -> {
            Investimento inv = c.getValue();
            double saldo = switch (inv.getTipo()) {
                case RENDA_FIXA -> saldoSvc.saldoAtual(inv, 0);
                case RENDA_VARIAVEL -> { var pos = rvSvc.calcular(inv.getId()); yield pos.vac() * pos.qc(); }
                case DOLAR -> calcDolarAtivo(inv);
            };
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(saldo));
        });
        colSaldo.setPrefWidth(140);

        TableColumn<Investimento, String> colPct = new TableColumn<>("% Carteira");
        colPct.setCellValueFactory(c -> {
            Investimento inv = c.getValue();
            double saldo = switch (inv.getTipo()) {
                case RENDA_FIXA -> saldoSvc.saldoAtual(inv, 0);
                case RENDA_VARIAVEL -> { var pos = rvSvc.calcular(inv.getId()); yield pos.vac() * pos.qc(); }
                case DOLAR -> calcDolarAtivo(inv);
            };
            double pct = pta > 0 ? saldo / pta * 100 : 0;
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.pct(pct));
        });
        colPct.setPrefWidth(100);

        table.getColumns().addAll(colNome, colTipo, colSaldo, colPct);
        table.getItems().addAll(invRepo.findAll());

        Label titleLbl = new Label("Ativos da Carteira");
        titleLbl.getStyleClass().add("section-title");
        VBox box = new VBox(8, titleLbl, table);
        box.getStyleClass().add("card");
        return box;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private double calcDolarTotal(double dolarBrl) {
        double total = 0;
        for (Investimento inv : invRepo.findByTipo(TipoInvestimento.DOLAR)) {
            double usd = 0;
            for (var mov : movRepo.findByInvestimento(inv.getId()))
                usd += switch (mov.getTipoMov()) {
                    case DEPOSITO -> mov.getValor();
                    case SAQUE -> -mov.getValor();
                };
            total += usd * dolarBrl;
        }
        return total;
    }

    private double calcDolarAtivo(Investimento inv) {
        double usd = 0;
        for (var mov : movRepo.findByInvestimento(inv.getId()))
            usd += switch (mov.getTipoMov()) {
                case DEPOSITO -> mov.getValor();
                case SAQUE -> -mov.getValor();
            };
        double cotacao = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorCompra).orElse(0.0);
        return usd * cotacao;
    }

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
}
