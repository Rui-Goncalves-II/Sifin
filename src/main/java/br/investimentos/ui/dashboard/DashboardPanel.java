package br.investimentos.ui.dashboard;

import br.investimentos.model.CotacaoDolar;
import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.ui.util.FormatUtil;
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
import org.jfree.chart.plot.PlotOrientation;
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
    private VBox summaryBox;
    private VBox alertBox;
    private HBox yearBar;
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
        // Header
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

        // Alerts
        alertBox = new VBox(4);
        alertBox.setPadding(new Insets(0, 24, 0, 24));

        // Content
        ScrollPane scroll = new ScrollPane(buildContent());
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

    private VBox buildContent() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 24, 24));

        summaryBox = new VBox(16);
        content.getChildren().addAll(alertBox, summaryBox);
        return content;
    }

    private void refresh() {
        ConsolidacaoService.ResultadoConsolidado r = consolSvc.calcular(anoSelecionado);
        summaryBox.getChildren().clear();

        // Summary cards grid
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(4, 0, 4, 0));

        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(33.33);
        col.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col, col, col);

        // Column headers
        addSectionHeader(grid, "🏦 RENDA FIXA", "section-rf", 0);
        addSectionHeader(grid, "💹 RENDA VARIÁVEL", "section-rv", 1);
        addSectionHeader(grid, "📊 CONSOLIDADO", "section-cons", 2);

        // Row 1 — valores investidos/aplicados
        addCard(grid, "Valor Investido", FormatUtil.brl(r.viarf()), 1, 0, "neutral");
        addCard(grid, "Capital Aplicado", FormatUtil.brl(r.viarv()), 1, 1, "neutral");
        addCard(grid, "Total Aplicado", FormatUtil.brl(r.vtia()), 1, 2, "neutral");

        // Row 2 — retornos
        addCard(grid, "Rendimentos", FormatUtil.brl(r.rarf()), 2, 0, r.rarf() >= 0 ? "positive" : "negative");
        addCard(grid, "Dividendos Recebidos", FormatUtil.brl(r.dta()), 2, 1, r.dta() >= 0 ? "positive" : "negative");
        addCard(grid, "Retorno Total", FormatUtil.brl(r.vtra()), 2, 2, r.vtra() >= 0 ? "positive" : "negative");

        // Row 3 — totais
        addCard(grid, "Saldo Total RF", FormatUtil.brl(r.vtarf()), 3, 0, "neutral");
        addCard(grid, "Posição Atual", FormatUtil.brl(r.parv()), 3, 1, "neutral");
        addCard(grid, "Patrimônio Total", FormatUtil.brl(r.pta()), 3, 2, "neutral");

        summaryBox.getChildren().add(grid);

        // PCPA e PRAT
        String labelAno = anoSelecionado == ConsolidacaoService.ANO_TODOS ? "no histórico total" : "esse ano";
        if (r.pcpa() != null) {
            Label pcpa = new Label((r.pcpa() >= 0 ? "▲" : "▼") + " Seu patrimônio cresceu " + FormatUtil.pct(Math.abs(r.pcpa())) + " " + labelAno);
            pcpa.getStyleClass().add(r.pcpa() >= 0 ? "summary-positive" : "summary-negative");
            pcpa.setMaxWidth(Double.MAX_VALUE);
            summaryBox.getChildren().add(pcpa);
        }
        if (r.prat() != null) {
            Label prat = new Label((r.prat() >= 0 ? "▲" : "▼") + " Você teve um rendimento total de " + FormatUtil.pct(Math.abs(r.prat())) + " " + labelAno);
            prat.getStyleClass().add(r.prat() >= 0 ? "summary-positive" : "summary-negative");
            prat.setMaxWidth(Double.MAX_VALUE);
            summaryBox.getChildren().add(prat);
        }

        // Charts
        HBox charts = buildCharts(r);
        summaryBox.getChildren().add(charts);

        // Assets table
        summaryBox.getChildren().add(buildAssetsCard(r.pta()));

        // Alerts
        atualizarAlertas();
    }

    private void addSectionHeader(GridPane grid, String text, String styleClass, int col) {
        Label lbl = new Label(text);
        lbl.getStyleClass().addAll("section-title", styleClass);
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.setPadding(new Insets(6, 10, 6, 10));
        GridPane.setColumnIndex(lbl, col);
        GridPane.setRowIndex(lbl, 0);
        grid.getChildren().add(lbl);
    }

    private void addCard(GridPane grid, String title, String value, int row, int col, String valueStyle) {
        String sectionStyle = switch (col) {
            case 0 -> "card-rf";
            case 1 -> "card-rv";
            default -> "card-cons";
        };
        VBox card = new VBox(4);
        card.getStyleClass().addAll("card", sectionStyle);
        Label t = new Label(title); t.getStyleClass().add("card-title");
        Label v = new Label(value); v.getStyleClass().addAll("card-value-sm", valueStyle);
        card.getChildren().addAll(t, v);
        GridPane.setColumnIndex(card, col);
        GridPane.setRowIndex(card, row);
        grid.getChildren().add(card);
    }

    private HBox buildCharts(ConsolidacaoService.ResultadoConsolidado r) {
        HBox hbox = new HBox(12);

        // Pie: RF vs RV vs Dolar
        DefaultPieDataset<String> pieData = new DefaultPieDataset<>();
        double rfVal = r.vtarf();
        double rvVal = r.vtarv();
        double usdVal = calcDolarTotal();
        if (rfVal > 0) pieData.setValue("Renda Fixa", rfVal);
        if (rvVal > 0) pieData.setValue("Renda Variável", rvVal);
        if (usdVal > 0) pieData.setValue("Dólar", usdVal);

        JFreeChart pie = ChartFactory.createPieChart("Alocação", pieData, true, false, false);
        pie.setBackgroundPaint(java.awt.Color.WHITE);
        SwingNode pieNode = new SwingNode();
        SwingUtilities.invokeLater(() -> pieNode.setContent(new ChartPanel(pie)));
        StackPane pieWrapper = new StackPane(pieNode);
        pieWrapper.setPrefSize(340, 240);

        // Bar: VTRA por mês (últimos 12 meses simplificado)
        DefaultCategoryDataset barData = buildMonthlyData();
        JFreeChart bar = ChartFactory.createBarChart("Retorno Mensal", "Mês", "R$", barData,
                PlotOrientation.VERTICAL, false, true, false);
        bar.setBackgroundPaint(java.awt.Color.WHITE);
        SwingNode barNode = new SwingNode();
        SwingUtilities.invokeLater(() -> barNode.setContent(new ChartPanel(bar)));
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

    private double calcDolarTotal() {
        double total = 0;
        for (Investimento inv : invRepo.findByTipo(TipoInvestimento.DOLAR)) {
            double usd = 0;
            for (var mov : movRepo.findByInvestimento(inv.getId())) {
                usd += switch (mov.getTipoMov()) {
                    case DEPOSITO -> mov.getValor();
                    case SAQUE -> -mov.getValor();
                };
            }
            double cotacao = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorCompra).orElse(0.0);
            total += usd * cotacao;
        }
        return total;
    }

    private DefaultCategoryDataset buildMonthlyData() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        LocalDate hoje = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            LocalDate d = hoje.minusMonths(i);
            int mes = d.getMonthValue();
            int ano = d.getYear();
            ConsolidacaoService.ResultadoConsolidado r = consolSvc.calcular(ano);
            // Aproximação: distribui VTRA/12 por mês do ano
            double vtra = r.vtra() / 12.0;
            ds.addValue(vtra, "Retorno", FormatUtil.mesAno(mes, ano));
        }
        return ds;
    }

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

        List<Investimento> todos = invRepo.findAll();
        table.getItems().addAll(todos);

        Label title = new Label("Ativos da Carteira");
        title.getStyleClass().add("section-title");
        VBox box = new VBox(8, title, table);
        box.getStyleClass().add("card");
        return box;
    }

    private double calcDolarAtivo(Investimento inv) {
        double usd = 0;
        for (var mov : movRepo.findByInvestimento(inv.getId())) {
            usd += switch (mov.getTipoMov()) {
                case DEPOSITO -> mov.getValor();
                case SAQUE -> -mov.getValor();
            };
        }
        double cotacao = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorCompra).orElse(0.0);
        return usd * cotacao;
    }

    private void atualizarAlertas() {
        alertBox.getChildren().clear();
        List<String> alertas = alertaSvc.alertasPendentes();
        for (String a : alertas) {
            Label lbl = new Label(a);
            lbl.getStyleClass().add("alert-bar");
            lbl.setMaxWidth(Double.MAX_VALUE);
            alertBox.getChildren().add(lbl);
        }
    }

    private void atualizarUsd() {
        cotacaoSvc.getCotacaoAtual().ifPresentOrElse(c -> {
            Platform.runLater(() -> usdLabel.setText(
                    "💰 USD: R$" + FormatUtil.numero(c.getValorCompra(), 2) + "/" + FormatUtil.numero(c.getValorVenda(), 2)));
        }, () -> usdLabel.setText("💰 USD: —"));
    }
}
