package br.investimentos.ui.rendavariavel;

import br.investimentos.model.AporteRv;
import br.investimentos.model.Investimento;
import br.investimentos.model.VacMensal;
import br.investimentos.model.enums.TipoOperacaoRv;
import br.investimentos.repository.*;
import br.investimentos.service.*;
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

import java.util.function.Consumer;

public class RendaVariavelDetalhePanel extends BorderPane {

    private final Investimento inv;
    private final InvestimentoRepository invRepo;
    private final AporteRvRepository aporteRepo;
    private final VacMensalRepository vacRepo;
    private final RendaVariavelService rvSvc;
    private final CotacaoService cotacaoSvc;
    private final Consumer<Node> navigate;

    public RendaVariavelDetalhePanel(Investimento inv, InvestimentoRepository invRepo,
                                      AporteRvRepository aporteRepo, VacMensalRepository vacRepo,
                                      RendaVariavelService rvSvc, CotacaoService cotacaoSvc,
                                      Consumer<Node> navigate) {
        this.inv = inv; this.invRepo = invRepo; this.aporteRepo = aporteRepo;
        this.vacRepo = vacRepo; this.rvSvc = rvSvc; this.cotacaoSvc = cotacaoSvc; this.navigate = navigate;
        construir();
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Button btnVoltar = new Button("← Voltar");
        btnVoltar.getStyleClass().add("btn-secondary");
        btnVoltar.setOnAction(e -> navigate.accept(new RendaVariavelListPanel(invRepo, aporteRepo, vacRepo, rvSvc, cotacaoSvc, navigate)));
        Label title = new Label("💹 " + inv.getNome());
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnVac = new Button("Atualizar VAC");
        btnVac.getStyleClass().add("btn-secondary");
        btnVac.setOnAction(e -> { new VacFormDialog(inv, vacRepo).showAndWait(); construir(); });
        Button btnOp = new Button("+ Operação");
        btnOp.getStyleClass().add("btn-primary");
        btnOp.setOnAction(e -> { new AporteRvFormDialog(inv, new AporteRv(), aporteRepo).showAndWait(); construir(); });
        header.getChildren().addAll(btnVoltar, title, spacer, btnVac, btnOp);
        setTop(header);

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    private VBox buildContent() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 24, 24));

        var pos = rvSvc.calcular(inv.getId());

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        ColumnConstraints cc = new ColumnConstraints(); cc.setPercentWidth(16.66); cc.setHgrow(Priority.ALWAYS);
        for (int i = 0; i < 6; i++) grid.getColumnConstraints().add(cc);

        grid.add(metricCard("VTP", FormatUtil.brl(pos.vtp()), "neutral"), 0, 0);
        grid.add(metricCard("QC", FormatUtil.qtd(pos.qc()), "neutral"), 1, 0);
        grid.add(metricCard("VMA", FormatUtil.brl(pos.vma()), "neutral"), 2, 0);
        grid.add(metricCard("VAC Atual", FormatUtil.brl(pos.vac()), "neutral"), 3, 0);
        grid.add(metricCard("Dividendos (D)", FormatUtil.brl(pos.d()), "positive"), 4, 0);
        grid.add(metricCard("PTG", FormatUtil.brl(pos.ptg()), "neutral"), 5, 0);
        grid.add(metricCard("LEB", FormatUtil.brl(pos.leb()), pos.leb() >= 0 ? "positive" : "negative"), 0, 1);
        grid.add(metricCard("LEB %", FormatUtil.pct(pos.lebPct()), pos.lebPct() >= 0 ? "positive" : "negative"), 1, 1);

        double lea = pos.leb() + pos.d();
        double leaPct = pos.vtp() > 0 ? (lea / pos.vtp()) * 100 : 0;
        grid.add(metricCard("LEA", FormatUtil.brl(lea), lea >= 0 ? "positive" : "negative"), 2, 1);
        grid.add(metricCard("LEA %", FormatUtil.pct(leaPct), leaPct >= 0 ? "positive" : "negative"), 3, 1);

        Label opTitle = new Label("Operações");
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
        cAcoes.setPrefWidth(150);
        cAcoes.setCellFactory(tc -> new TableCell<>() {
            final Button edit = new Button("✏ Editar");
            final Button del  = new Button("🗑 Excluir");
            {
                edit.getStyleClass().add("btn-secondary");
                del.getStyleClass().add("btn-danger");
                edit.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");
                del.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");
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
        java.util.List<AporteRv> ops = aporteRepo.findByInvestimento(inv.getId());
        java.util.Collections.reverse(ops);
        opTable.getItems().addAll(ops);

        HBox graficos = buildGraficos();
        content.getChildren().addAll(grid, graficos, opTitle, opTable);
        return content;
    }

    private HBox buildGraficos() {
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

        DefaultCategoryDataset vacDs = new DefaultCategoryDataset();
        for (VacMensal v : vacRepo.findByInvestimento(inv.getId()))
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

        JFreeChart vacChart = new JFreeChart("VAC por Mês", fontBold, vacPlot, false);
        vacChart.setBackgroundPaint(bgCard); vacChart.setBorderVisible(false);
        vacChart.getTitle().setPaint(textMut); vacChart.getTitle().setFont(fontBold);

        SwingNode vacNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(vacChart); cp.setBackground(bgCard); vacNode.setContent(cp);
        });
        StackPane vacPane = new StackPane(vacNode); vacPane.setPrefSize(400, 220);

        java.util.Map<String, Double> divMap = new java.util.LinkedHashMap<>();
        aporteRepo.findByInvestimento(inv.getId()).stream()
            .filter(a -> a.getTipoOp() == TipoOperacaoRv.DIVIDENDO)
            .sorted(java.util.Comparator.comparingInt(
                    (AporteRv a) -> a.getPeriodoAno() * 100 + a.getPeriodoMes()))
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

        JFreeChart divChart = new JFreeChart("Dividendos por Período", fontBold, divPlot, false);
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
