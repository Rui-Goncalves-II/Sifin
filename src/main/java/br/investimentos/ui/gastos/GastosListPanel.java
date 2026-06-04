package br.investimentos.ui.gastos;

import br.investimentos.model.Gasto;
import br.investimentos.model.enums.TipoGasto;
import br.investimentos.repository.GastoRepository;
import br.investimentos.service.GastosService;
import br.investimentos.service.GastosService.ResumoSegmento;
import br.investimentos.ui.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GastosListPanel extends BorderPane {

    private static final String[] MESES_ABREV = {
        "", "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
        "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    };
    private static final int MES_TODOS = -1;

    private final GastoRepository repo;
    private final GastosService svc;
    private final TipoGasto tipo;
    private final Runnable onAtualizado;

    private int anoSelecionado;
    private int mesSelecionado;

    private HBox monthBar;
    private VBox contentBox;
    private TableView<Gasto> table;

    public GastosListPanel(GastoRepository repo, GastosService svc, TipoGasto tipo, Runnable onAtualizado) {
        this.repo = repo;
        this.svc = svc;
        this.tipo = tipo;
        this.onAtualizado = onAtualizado;
        this.anoSelecionado = LocalDate.now().getYear();
        this.mesSelecionado = LocalDate.now().getMonthValue();
        construir();
    }

    private void construir() {
        Button btnNovo = new Button("+ Novo");
        btnNovo.getStyleClass().add("btn-primary");
        btnNovo.setOnAction(e -> abrirForm(null));

        Label title = new Label(tipo.getLabel());
        title.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, title, spacer, btnNovo);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        setTop(header);

        monthBar = new HBox(4);
        monthBar.setAlignment(Pos.CENTER_LEFT);
        buildMonthBar();

        HBox filterRow = new HBox(12, monthBar);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.setPadding(new Insets(8, 24, 4, 24));

        table = buildTable();

        contentBox = new VBox(12);
        contentBox.setPadding(new Insets(8, 24, 24, 24));

        VBox scrollContent = new VBox(0, filterRow, contentBox);
        ScrollPane scroll = new ScrollPane(scrollContent);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);

        refresh();
    }

    private void buildMonthBar() {
        monthBar.getChildren().clear();
        for (int m = 1; m <= 12; m++) {
            Button btn = new Button(MESES_ABREV[m]);
            btn.getStyleClass().add("year-btn");
            if (m == mesSelecionado) btn.getStyleClass().add("active");
            int mes = m;
            btn.setOnAction(e -> { mesSelecionado = mes; buildMonthBar(); refresh(); });
            monthBar.getChildren().add(btn);
        }
        Button todos = new Button("Todos os meses");
        todos.getStyleClass().add("year-btn");
        if (mesSelecionado == MES_TODOS) todos.getStyleClass().add("active");
        todos.setOnAction(e -> { mesSelecionado = MES_TODOS; buildMonthBar(); refresh(); });
        monthBar.getChildren().add(todos);
    }

    public void refresh() {
        ResumoSegmento resumo = svc.calcularResumoSegmento(tipo, mesSelecionado, anoSelecionado);
        List<Gasto> gastos = svc.listarGastos(tipo, mesSelecionado, anoSelecionado);

        contentBox.getChildren().clear();
        contentBox.getChildren().add(buildKpiRow(resumo));
        contentBox.getChildren().add(buildChart(gastos));

        table.getItems().setAll(gastos);
        VBox.setVgrow(table, Priority.ALWAYS);
        contentBox.getChildren().add(table);
    }

    // ── KPI cards ────────────────────────────────────────────────────────

    private HBox buildKpiRow(ResumoSegmento r) {
        String labelTotal = mesSelecionado == MES_TODOS ? "Total do ano"     : "Total do mês";
        String labelCount = mesSelecionado == MES_TODOS ? "Nº de gastos"     : "Nº de gastos";
        String labelMaior = mesSelecionado == MES_TODOS ? "Maior gasto (ano)": "Maior gasto";
        String labelMedia = mesSelecionado == MES_TODOS ? "Média por gasto"  : "Média por gasto";

        String cor = accentColor();
        VBox kTotal = makeKpiCard(labelTotal,  FormatUtil.brl(r.total()),      "GT — " + tipo.getLabel(), cor);
        VBox kCount = makeKpiCard(labelCount,  String.valueOf(r.count()),      "registros",               cor);
        VBox kMaior = makeKpiCard(labelMaior,  FormatUtil.brl(r.maiorValor()), r.maiorDesc(),             cor);
        VBox kMedia = makeKpiCard(labelMedia,  FormatUtil.brl(r.media()),      "por lançamento",          cor);

        HBox row = new HBox(12, kTotal, kCount, kMaior, kMedia);
        for (VBox card : new VBox[]{kTotal, kCount, kMaior, kMedia}) {
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
        }
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
        sub.setMaxWidth(Double.MAX_VALUE);

        card.getChildren().addAll(lbl, val, sub);
        return card;
    }

    // ── Gráfico ──────────────────────────────────────────────────────────

    private VBox buildChart(List<Gasto> gastos) {
        if (mesSelecionado == MES_TODOS) {
            return buildMonthlyBarChart(gastos);
        } else {
            return buildRankingBarChart(gastos);
        }
    }

    private VBox buildRankingBarChart(List<Gasto> gastos) {
        java.awt.Color bgCard  = new java.awt.Color(0x16, 0x1b, 0x22);
        java.awt.Color border  = new java.awt.Color(0x2a, 0x34, 0x41);
        java.awt.Color textMut = new java.awt.Color(0x7d, 0x8f, 0xa0);
        java.awt.Color accent  = awtAccentColor();
        java.awt.Font  fontXs  = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        java.awt.Font  fontBold= new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        Map<String, Double> totaisPorDesc = new LinkedHashMap<>();
        for (Gasto g : gastos) {
            String desc = g.getDescricao().length() > 25
                    ? g.getDescricao().substring(0, 24) + "…"
                    : g.getDescricao();
            totaisPorDesc.merge(desc, g.getValor(), Double::sum);
        }
        totaisPorDesc.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> ds.addValue(e.getValue(), "Valor", e.getKey()));

        CategoryAxis xAxis = new CategoryAxis(null);
        xAxis.setTickLabelPaint(textMut);
        xAxis.setTickLabelFont(fontXs);
        xAxis.setAxisLinePaint(border);
        xAxis.setTickMarkPaint(border);
        xAxis.setMaximumCategoryLabelLines(2);

        NumberAxis yAxis = new NumberAxis("R$");
        yAxis.setTickLabelPaint(textMut);
        yAxis.setTickLabelFont(fontXs);
        yAxis.setAxisLinePaint(border);
        yAxis.setTickMarkPaint(border);
        yAxis.setLabelPaint(textMut);
        yAxis.setLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        yAxis.setAutoRangeIncludesZero(true);

        BarRenderer renderer = new BarRenderer() {
            @Override public java.awt.Paint getItemPaint(int row, int col) { return accent; }
        };
        renderer.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setDefaultItemLabelGenerator(
                new org.jfree.chart.labels.StandardCategoryItemLabelGenerator(
                        "{2}", java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("pt", "BR"))));
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelPaint(textMut);
        renderer.setDefaultItemLabelFont(fontXs);
        renderer.setDefaultPositiveItemLabelPosition(
                new org.jfree.chart.labels.ItemLabelPosition(
                        org.jfree.chart.labels.ItemLabelAnchor.OUTSIDE12,
                        org.jfree.chart.ui.TextAnchor.BOTTOM_CENTER));

        CategoryPlot plot = new CategoryPlot(ds, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(bgCard);
        plot.setOutlinePaint(border);
        plot.setRangeGridlinePaint(border);
        plot.setDomainGridlinesVisible(false);
        plot.setOrientation(PlotOrientation.VERTICAL);

        String tituloMes = mesSelecionado != MES_TODOS
                ? "Top gastos — " + MESES_ABREV[mesSelecionado] + "/" + anoSelecionado
                : "Top gastos";
        JFreeChart chart = new JFreeChart(tituloMes, fontBold, plot, false);
        chart.setBackgroundPaint(bgCard);
        chart.setBorderVisible(false);
        chart.getTitle().setPaint(textMut);
        chart.getTitle().setFont(fontBold);

        return wrapChart(chart, bgCard, Math.max(200, Math.min(10, totaisPorDesc.size()) * 36 + 80));
    }

    private VBox buildMonthlyBarChart(List<Gasto> gastos) {
        java.awt.Color bgCard  = new java.awt.Color(0x16, 0x1b, 0x22);
        java.awt.Color border  = new java.awt.Color(0x2a, 0x34, 0x41);
        java.awt.Color textMut = new java.awt.Color(0x7d, 0x8f, 0xa0);
        java.awt.Color accent  = awtAccentColor();
        java.awt.Font  fontXs  = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        java.awt.Font  fontBold= new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);

        double[] totaisMes = new double[13];
        for (Gasto g : gastos) {
            if (g.getPeriodoMes() != null) totaisMes[g.getPeriodoMes()] += g.getValor();
        }

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (int m = 1; m <= 12; m++) {
            ds.addValue(totaisMes[m], tipo.getLabel(), MESES_ABREV[m]);
        }

        CategoryAxis xAxis = new CategoryAxis(null);
        xAxis.setTickLabelPaint(textMut);
        xAxis.setTickLabelFont(fontXs);
        xAxis.setAxisLinePaint(border);
        xAxis.setTickMarkPaint(border);

        NumberAxis yAxis = new NumberAxis("R$");
        yAxis.setTickLabelPaint(textMut);
        yAxis.setTickLabelFont(fontXs);
        yAxis.setAxisLinePaint(border);
        yAxis.setTickMarkPaint(border);
        yAxis.setLabelPaint(textMut);
        yAxis.setLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
        yAxis.setAutoRangeIncludesZero(true);

        BarRenderer renderer = new BarRenderer() {
            @Override public java.awt.Paint getItemPaint(int row, int col) { return accent; }
        };
        renderer.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());
        renderer.setShadowVisible(false);

        CategoryPlot plot = new CategoryPlot(ds, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(bgCard);
        plot.setOutlinePaint(border);
        plot.setRangeGridlinePaint(border);
        plot.setDomainGridlinesVisible(false);
        plot.setOrientation(PlotOrientation.VERTICAL);

        JFreeChart chart = new JFreeChart(
                tipo.getLabel() + " por mês — " + anoSelecionado, fontBold, plot, false);
        chart.setBackgroundPaint(bgCard);
        chart.setBorderVisible(false);
        chart.getTitle().setPaint(textMut);
        chart.getTitle().setFont(fontBold);

        return wrapChart(chart, bgCard, 240);
    }

    private VBox wrapChart(JFreeChart chart, java.awt.Color bg, int height) {
        SwingNode node = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(chart);
            cp.setBackground(bg);
            node.setContent(cp);
        });
        StackPane wrapper = new StackPane(node);
        wrapper.setPrefHeight(height);
        VBox box = new VBox(wrapper);
        box.getStyleClass().add("card");
        box.setPadding(Insets.EMPTY);
        return box;
    }

    // ── Tabela ───────────────────────────────────────────────────────────

    private TableView<Gasto> buildTable() {
        TableView<Gasto> t = new TableView<>();
        t.getStyleClass().add("table-view");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label("Nenhum registro de " + tipo.getLabel().toLowerCase() + " no período."));

        TableColumn<Gasto, String> cDesc = new TableColumn<>("Descrição");
        cDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescricao()));
        cDesc.setPrefWidth(200);

        TableColumn<Gasto, String> cPer = new TableColumn<>("Período");
        cPer.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().getPeriodoMes(), c.getValue().getPeriodoAno())));
        cPer.setPrefWidth(90);

        TableColumn<Gasto, String> cParc = new TableColumn<>("Parcela");
        cParc.setCellValueFactory(c -> {
            Gasto g = c.getValue();
            if (!g.isParcelado()) return new SimpleStringProperty("—");
            return new SimpleStringProperty(g.getParcelaNumero() + "/" + g.getParcelasTotal());
        });
        cParc.setPrefWidth(70);
        cParc.setStyle("-fx-alignment: CENTER;");

        TableColumn<Gasto, String> cValor = new TableColumn<>("Valor");
        cValor.setCellValueFactory(c -> new SimpleStringProperty(FormatUtil.brl(c.getValue().getValor())));
        cValor.setPrefWidth(110);
        cValor.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<Gasto, String> cNotas = new TableColumn<>("Notas");
        cNotas.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNotas() != null ? c.getValue().getNotas() : ""));

        TableColumn<Gasto, Void> cAcoes = new TableColumn<>("Ações");
        cAcoes.setMinWidth(148);
        cAcoes.setPrefWidth(148);
        cAcoes.setMaxWidth(148);
        cAcoes.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("Editar");
            private final Button btnDel  = new Button("Excluir");
            private final HBox box = new HBox(6, btnEdit, btnDel);
            {
                btnEdit.getStyleClass().add("btn-secondary");
                btnDel.getStyleClass().add("btn-danger");
                btnEdit.setMinWidth(Region.USE_PREF_SIZE);
                btnDel.setMinWidth(Region.USE_PREF_SIZE);
                box.setAlignment(Pos.CENTER);
                btnEdit.setOnAction(e -> abrirForm(getTableView().getItems().get(getIndex())));
                btnDel.setOnAction(e -> excluir(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        t.getColumns().addAll(cDesc, cPer, cParc, cValor, cNotas, cAcoes);
        return t;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────

    private void abrirForm(Gasto existente) {
        GastoFormDialog dlg = new GastoFormDialog(getScene().getWindow(), repo, tipo, existente);
        dlg.setOnSalvo(this::atualizar);
        dlg.showAndWait();
    }

    private void excluir(Gasto g) {
        if (g.isParcelado()) {
            excluirParcelado(g);
        } else {
            excluirSimples(g);
        }
    }

    private void excluirSimples(Gasto g) {
        Optional<ButtonType> res = new Alert(Alert.AlertType.CONFIRMATION,
                "Excluir o gasto \"" + g.getDescricao() + "\" de " + FormatUtil.brl(g.getValor()) + "?",
                ButtonType.YES, ButtonType.NO).showAndWait();
        if (res.isPresent() && res.get() == ButtonType.YES) {
            repo.deletar(g.getId());
            atualizar();
        }
    }

    private void excluirParcelado(Gasto g) {
        ButtonType soParcela    = new ButtonType("Só esta parcela",   ButtonBar.ButtonData.LEFT);
        ButtonType todasParcelas= new ButtonType("Todas as parcelas", ButtonBar.ButtonData.LEFT);
        ButtonType cancelar     = new ButtonType("Cancelar",          ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
        dlg.setTitle("Excluir parcela");
        dlg.setHeaderText("\"" + g.getDescricao() + "\" — parcela " + g.getParcelaNumero() + "/" + g.getParcelasTotal());
        dlg.setContentText("Deseja excluir apenas esta parcela ou todas as " + g.getParcelasTotal() + " parcelas?");
        dlg.getButtonTypes().setAll(soParcela, todasParcelas, cancelar);

        Optional<ButtonType> res = dlg.showAndWait();
        if (res.isEmpty() || res.get() == cancelar) return;

        if (res.get() == todasParcelas) repo.deletarGrupo(g.getGrupoParcela());
        else repo.deletar(g.getId());
        atualizar();
    }

    private void atualizar() {
        refresh();
        if (onAtualizado != null) onAtualizado.run();
    }

    public void carregar() {
        refresh();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String accentColor() {
        return tipo == TipoGasto.ALIMENTAR ? "#e3b341" : "#58a6ff";
    }

    private java.awt.Color awtAccentColor() {
        return tipo == TipoGasto.ALIMENTAR
                ? new java.awt.Color(0xe3, 0xb3, 0x41)
                : new java.awt.Color(0x58, 0xa6, 0xff);
    }
}
