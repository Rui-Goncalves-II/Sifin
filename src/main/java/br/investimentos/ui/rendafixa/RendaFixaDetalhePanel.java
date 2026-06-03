package br.investimentos.ui.rendafixa;

import br.investimentos.model.Investimento;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.VtaMensal;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.ui.projecao.ProjecaoPanel;
import br.investimentos.ui.util.FormatUtil;
import br.investimentos.ui.util.GlossarioTooltip;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.*;
import javafx.embed.swing.SwingNode;
import javafx.util.StringConverter;
import javax.swing.SwingUtilities;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import java.util.function.Consumer;

public class RendaFixaDetalhePanel extends BorderPane {

    private static final int ANO_TODOS = -1;

    private final Investimento inv;
    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final VtaMensalRepository vtaRepo;
    private final VaiAnualRepository vaiRepo;
    private final RendimentoService rendSvc;
    private final TaxaService taxaSvc;
    private final SaldoService saldoSvc;
    private final VaiService vaiSvc;
    private final Consumer<Node> navigate;

    private int anoSelecionado;

    public RendaFixaDetalhePanel(Investimento inv, InvestimentoRepository invRepo,
                                  MovimentacaoRepository movRepo, VtaMensalRepository vtaRepo,
                                  VaiAnualRepository vaiRepo, RendimentoService rendSvc,
                                  TaxaService taxaSvc, SaldoService saldoSvc,
                                  VaiService vaiSvc, Consumer<Node> navigate) {
        this.inv = inv; this.invRepo = invRepo; this.movRepo = movRepo;
        this.vtaRepo = vtaRepo; this.vaiRepo = vaiRepo; this.rendSvc = rendSvc;
        this.taxaSvc = taxaSvc; this.saldoSvc = saldoSvc; this.vaiSvc = vaiSvc;
        this.navigate = navigate;
        anoSelecionado = LocalDate.now().getYear();
        construir();
    }

    private List<Integer> anosComDados() {
        TreeSet<Integer> anos = new TreeSet<>();
        vtaRepo.findByInvestimento(inv.getId()).forEach(v -> anos.add(v.getPeriodoAno()));
        movRepo.findByInvestimento(inv.getId()).forEach(m -> anos.add(m.getPeriodoAno()));
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

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Button btnVoltar = new Button("← Voltar");
        btnVoltar.getStyleClass().add("btn-secondary");
        btnVoltar.setOnAction(e -> navigate.accept(new RendaFixaListPanel(
                invRepo, movRepo, vtaRepo, vaiRepo, rendSvc, taxaSvc, saldoSvc, vaiSvc, navigate)));
        Label title = new Label("🏦 " + inv.getNome());
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnVta = new Button("+ VTA");
        btnVta.getStyleClass().add("btn-primary");
        btnVta.setOnAction(e -> {
            new VtaFormDialog(inv, vtaRepo, vaiRepo, rendSvc, vaiSvc).showAndWait();
            construir();
        });
        Button btnProjecao = new Button("📈 Projeção");
        btnProjecao.getStyleClass().add("btn-secondary");
        btnProjecao.setOnAction(e -> {
            ProjecaoService projecaoSvc = new ProjecaoService(vtaRepo, taxaSvc, rendSvc);
            navigate.accept(new ProjecaoPanel(invRepo, projecaoSvc, navigate));
        });
        header.getChildren().addAll(btnVoltar, title, spacer, buildYearCombo(), btnProjecao, btnVta);
        setTop(header);

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    private VBox buildContent() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 24, 24));

        LocalDate hoje = LocalDate.now();
        int mesAtual = hoje.getMonthValue();
        int anoAtual = hoje.getYear();
        double saldoAtual = saldoSvc.saldoAtual(inv, 0);

        // ── Summary cards ────────────────────────────────────────────────
        GridPane sumGrid = new GridPane();
        sumGrid.setHgap(12); sumGrid.setVgap(12);
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(25); cc.setHgrow(Priority.ALWAYS);
        sumGrid.getColumnConstraints().addAll(cc, cc, cc, cc);

        if (anoSelecionado != ANO_TODOS) {
            int ateOMes = (anoSelecionado == anoAtual) ? mesAtual : 12;
            double vai = vaiRepo.getVai(inv.getId(), anoSelecionado);
            double sam = rendSvc.calcularSam(inv.getId(), anoSelecionado, ateOMes);
            double r = vtaRepo.findUltimoDoAno(inv.getId(), anoSelecionado)
                    .map(rendSvc::calcularR).orElse(0.0);
            String samLabel = (anoSelecionado == anoAtual)
                    ? "SAM " + FormatUtil.mesAno(mesAtual, anoSelecionado)
                    : "SAM " + anoSelecionado;

            sumGrid.add(metricCard("Saldo Atual", FormatUtil.brl(saldoAtual), "neutral"), 0, 0);
            sumGrid.add(metricCard("VAI " + anoSelecionado, FormatUtil.brl(vai), "neutral"), 1, 0);
            sumGrid.add(metricCard(samLabel, FormatUtil.brl(sam), "neutral"), 2, 0);
            sumGrid.add(metricCard("Rendimento (R)", FormatUtil.brl(r), r >= 0 ? "positive" : "negative"), 3, 0);
        } else {
            double totalAportado = rendSvc.calcularTotalAportado(inv.getId());
            double rTotal = rendSvc.calcularRTotal(inv.getId(), anosComDados());

            String pctStr = (totalAportado > 0)
                    ? FormatUtil.pct(rTotal / totalAportado * 100)
                    : "—";

            sumGrid.add(metricCard("Saldo Atual", FormatUtil.brl(saldoAtual), "neutral"), 0, 0);
            sumGrid.add(metricCard("Total Aportado", FormatUtil.brl(totalAportado), "neutral"), 1, 0);
            sumGrid.add(metricCard("Total Rendimento", FormatUtil.brl(rTotal), rTotal >= 0 ? "positive" : "negative"), 2, 0);
            sumGrid.add(metricCard("Rendimento %", pctStr, rTotal >= 0 ? "positive" : "negative"), 3, 0);
        }

        // Info do ativo
        String subInfo = (inv.getSubtipo() != null ? inv.getSubtipo() : "—") +
                " / " + (inv.getIndexador() != null ? inv.getIndexador() : "—");
        String taxaInfo = inv.getTaxaAnual() != null ? FormatUtil.pct(inv.getTaxaAnual()) + " a.a." : "calculada";
        Label info = new Label("Subtipo: " + subInfo + "  |  Taxa: " + taxaInfo +
                (inv.getDataVencimento() != null ? "  |  Vencimento: " + inv.getDataVencimento() : ""));
        info.getStyleClass().add("card-label");

        // Filtered VTA list
        List<VtaMensal> vtas = (anoSelecionado != ANO_TODOS)
                ? vtaRepo.findByInvestimentoEAno(inv.getId(), anoSelecionado)
                : vtaRepo.findByInvestimento(inv.getId());

        Map<Integer, Double> somaAntByVtaId = new HashMap<>();
        double prevR = 0;
        int prevAno = -1;
        for (VtaMensal v : vtas) {
            if (v.getPeriodoAno() != prevAno) { prevAno = v.getPeriodoAno(); prevR = 0; }
            somaAntByVtaId.put(v.getId(), prevR);
            prevR = rendSvc.calcularR(v);
        }

        HBox graficos = buildGraficos(vtas, somaAntByVtaId);

        // Histórico VTA
        String histLabel = (anoSelecionado != ANO_TODOS)
                ? "Histórico de VTA — " + anoSelecionado
                : "Histórico de VTA — Total";
        Label histTitle = new Label(histLabel);
        histTitle.getStyleClass().add("section-title");

        TableView<VtaMensal> vtaTable = new TableView<>();
        vtaTable.getStyleClass().add("table-view");
        vtaTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        vtaTable.setPrefHeight(300);

        TableColumn<VtaMensal, String> cPer = new TableColumn<>("Período");
        cPer.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().getPeriodoMes(), c.getValue().getPeriodoAno())));
        cPer.setPrefWidth(100);

        TableColumn<VtaMensal, String> cVta = new TableColumn<>();
        cVta.setGraphic(colHeader("VTA"));
        cVta.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(c.getValue().getVta())));
        cVta.setPrefWidth(130);

        TableColumn<VtaMensal, String> cVi = new TableColumn<>();
        cVi.setGraphic(colHeader("VI"));
        cVi.setCellValueFactory(c -> {
            VtaMensal v = c.getValue();
            double viVal = rendSvc.calcularVi(v.getInvestimentoId(), v.getPeriodoAno(), v.getPeriodoMes());
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(viVal));
        });
        cVi.setPrefWidth(130);

        TableColumn<VtaMensal, String> cR = new TableColumn<>("Rend. Acumulado");
        cR.setCellValueFactory(c -> {
            double rVal = rendSvc.calcularR(c.getValue());
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(rVal));
        });
        cR.setPrefWidth(130);

        TableColumn<VtaMensal, String> cSomaPrev = new TableColumn<>("Rend. Ant.");
        cSomaPrev.setCellValueFactory(c -> {
            double soma = somaAntByVtaId.getOrDefault(c.getValue().getId(), 0.0);
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(soma));
        });
        cSomaPrev.setPrefWidth(120);

        TableColumn<VtaMensal, String> cRMensal = new TableColumn<>("Rend. Mensal");
        cRMensal.setCellValueFactory(c -> {
            VtaMensal v = c.getValue();
            double rMensal = rendSvc.calcularR(v) - somaAntByVtaId.getOrDefault(v.getId(), 0.0);
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(rMensal));
        });
        cRMensal.setPrefWidth(120);

        TableColumn<VtaMensal, String> cTaxa = new TableColumn<>("Taxa Acum.");
        cTaxa.setCellValueFactory(c -> {
            VtaMensal v = c.getValue();
            double viVal = rendSvc.calcularVi(v.getInvestimentoId(), v.getPeriodoAno(), v.getPeriodoMes());
            double rVal = rendSvc.calcularR(v);
            double ta = taxaSvc.taxaAcumulada(rVal, viVal);
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.pct(ta * 100));
        });
        cTaxa.setPrefWidth(100);

        TableColumn<VtaMensal, Void> cAcoes = new TableColumn<>("Ações");
        cAcoes.setMinWidth(160);
        cAcoes.setPrefWidth(160);
        cAcoes.setMaxWidth(160);
        cAcoes.setCellFactory(col -> new TableCell<>() {
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
                    VtaMensal v = getTableView().getItems().get(getIndex());
                    new VtaFormDialog(inv, vtaRepo, vaiRepo, rendSvc, vaiSvc, v).showAndWait();
                    construir();
                });

                btnDel.setOnAction(e -> {
                    VtaMensal v = getTableView().getItems().get(getIndex());
                    Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                            "Excluir VTA de " + FormatUtil.mesAno(v.getPeriodoMes(), v.getPeriodoAno()) + "?",
                            ButtonType.YES, ButtonType.NO);
                    conf.setTitle("Confirmar exclusão");
                    conf.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) {
                            vtaRepo.deletar(v.getId());
                            construir();
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : new HBox(6, btnEdit, btnDel));
            }
        });

        vtaTable.getColumns().addAll(cPer, cVta, cVi, cR, cSomaPrev, cRMensal, cTaxa, cAcoes);
        vtaTable.getItems().addAll(vtas);

        // ── Movimentações ────────────────────────────────────────────────
        String movLabel = (anoSelecionado != ANO_TODOS)
                ? "Movimentações — " + anoSelecionado
                : "Movimentações — Total";
        Label movTitle = new Label(movLabel);
        movTitle.getStyleClass().add("section-title");

        Button btnNovaMov = new Button("+ Movimentação");
        btnNovaMov.getStyleClass().add("btn-primary");
        btnNovaMov.setStyle("-fx-font-size: 16px; -fx-padding: 5 14;");
        btnNovaMov.setOnAction(e -> {
            Movimentacao nova = new Movimentacao();
            new MovimentacaoFormDialog(inv, nova, movRepo).showAndWait();
            construir();
        });
        HBox movHeader = new HBox(12, movTitle, btnNovaMov);
        movHeader.setAlignment(Pos.CENTER_LEFT);

        TableView<Movimentacao> movTable = new TableView<>();
        movTable.getStyleClass().add("table-view");
        movTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        movTable.setPrefHeight(200);

        TableColumn<Movimentacao, String> mPer = new TableColumn<>("Período");
        mPer.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().getPeriodoMes(), c.getValue().getPeriodoAno())));
        mPer.setPrefWidth(100);

        TableColumn<Movimentacao, String> mTipo = new TableColumn<>("Tipo");
        mTipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getTipoMov() == TipoMovimentacao.DEPOSITO ? "Depósito" : "Saque"));
        mTipo.setPrefWidth(100);

        TableColumn<Movimentacao, String> mValor = new TableColumn<>("Valor");
        mValor.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().getValor())));
        mValor.setPrefWidth(130);
        mValor.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                boolean entrada = getTableRow().getItem().getTipoMov() == TipoMovimentacao.DEPOSITO;
                setText(item + (entrada ? " ▲" : " ▼"));
                setStyle("-fx-text-fill: " + (entrada ? "#2e7d32" : "#c62828") + ";");
            }
        });

        TableColumn<Movimentacao, String> mNotas = new TableColumn<>("Notas");
        mNotas.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getNotas() != null ? c.getValue().getNotas() : ""));
        mNotas.setPrefWidth(200);

        TableColumn<Movimentacao, Void> mAcoes = new TableColumn<>("Ações");
        mAcoes.setPrefWidth(150);
        mAcoes.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("✏ Editar");
            private final Button btnDel  = new Button("🗑 Excluir");
            {
                btnEdit.getStyleClass().add("btn-secondary");
                btnDel.getStyleClass().add("btn-danger");
                btnEdit.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");
                btnDel.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");

                btnEdit.setOnAction(e -> {
                    Movimentacao m = getTableView().getItems().get(getIndex());
                    new MovimentacaoFormDialog(inv, m, movRepo).showAndWait();
                    construir();
                });

                btnDel.setOnAction(e -> {
                    Movimentacao m = getTableView().getItems().get(getIndex());
                    Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                            "Excluir movimentação de " + FormatUtil.brl(m.getValor()) + "?",
                            ButtonType.YES, ButtonType.NO);
                    conf.setTitle("Confirmar exclusão");
                    conf.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) {
                            movRepo.deletar(m.getId());
                            construir();
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : new HBox(6, btnEdit, btnDel));
            }
        });

        movTable.getColumns().addAll(mPer, mTipo, mValor, mNotas, mAcoes);
        List<Movimentacao> movs = (anoSelecionado != ANO_TODOS)
                ? movRepo.findByInvestimentoEAno(inv.getId(), anoSelecionado)
                : movRepo.findByInvestimento(inv.getId());
        java.util.Collections.reverse(movs);
        movTable.getItems().addAll(movs);

        content.getChildren().addAll(new VBox(4, info), sumGrid, graficos, histTitle, vtaTable,
                movHeader, movTable);
        return content;
    }

    private HBox buildGraficos(List<VtaMensal> vtas, Map<Integer, Double> somaAntByVtaId) {
        java.awt.Color bgCard  = new java.awt.Color(0x16, 0x1b, 0x22);
        java.awt.Color border  = new java.awt.Color(0x2a, 0x34, 0x41);
        java.awt.Color textMut = new java.awt.Color(0x7d, 0x8f, 0xa0);
        java.awt.Color green   = new java.awt.Color(0x3f, 0xb9, 0x50);
        java.awt.Color red     = new java.awt.Color(0xf8, 0x51, 0x49);
        java.awt.Color blue    = new java.awt.Color(0x58, 0xa6, 0xff);
        java.awt.Font  fontXs   = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        java.awt.Font  fontBold = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12);
        java.awt.BasicStroke stroke = new java.awt.BasicStroke(
                1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND);
        java.awt.geom.Ellipse2D.Double dot = new java.awt.geom.Ellipse2D.Double(-5, -5, 10, 10);

        // ── Saldo (VTA) por Mês ──────────────────────────────────────────
        DefaultCategoryDataset saldoDs = new DefaultCategoryDataset();
        for (VtaMensal v : vtas)
            saldoDs.addValue(v.getVta(), "Saldo", FormatUtil.mesAno(v.getPeriodoMes(), v.getPeriodoAno()));

        org.jfree.chart.renderer.category.LineAndShapeRenderer saldoRend =
            new org.jfree.chart.renderer.category.LineAndShapeRenderer() {
                @Override public java.awt.Paint getItemPaint(int row, int col) { return blue; }
                @Override public java.awt.Paint getItemFillPaint(int row, int col) { return blue; }
            };
        saldoRend.setDefaultLinesVisible(true); saldoRend.setDefaultShapesVisible(true);
        saldoRend.setDefaultShapesFilled(true); saldoRend.setUseFillPaint(true);
        saldoRend.setDefaultShape(dot); saldoRend.setDefaultStroke(stroke);

        org.jfree.chart.axis.CategoryAxis xS = new org.jfree.chart.axis.CategoryAxis(null);
        xS.setTickLabelPaint(textMut); xS.setTickLabelFont(fontXs);
        xS.setAxisLinePaint(border); xS.setTickMarkPaint(border);
        org.jfree.chart.axis.NumberAxis yS = new org.jfree.chart.axis.NumberAxis("R$");
        yS.setTickLabelPaint(textMut); yS.setTickLabelFont(fontXs);
        yS.setAxisLinePaint(border); yS.setTickMarkPaint(border);
        yS.setAutoRangeIncludesZero(false);

        org.jfree.chart.plot.CategoryPlot saldoPlot = new org.jfree.chart.plot.CategoryPlot(saldoDs, xS, yS, saldoRend);
        saldoPlot.setBackgroundPaint(bgCard); saldoPlot.setOutlinePaint(border);
        saldoPlot.setRangeGridlinePaint(border); saldoPlot.setDomainGridlinesVisible(false);

        String saldoTitle = (anoSelecionado != ANO_TODOS)
                ? "Saldo por Mês — " + anoSelecionado
                : "Saldo por Mês — Total";
        JFreeChart saldoChart = new JFreeChart(saldoTitle, fontBold, saldoPlot, false);
        saldoChart.setBackgroundPaint(bgCard); saldoChart.setBorderVisible(false);
        saldoChart.getTitle().setPaint(textMut); saldoChart.getTitle().setFont(fontBold);

        SwingNode saldoNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(saldoChart); cp.setBackground(bgCard); saldoNode.setContent(cp);
        });
        StackPane saldoPane = new StackPane(saldoNode); saldoPane.setPrefSize(400, 220);

        // ── Rendimento Mensal ────────────────────────────────────────────
        DefaultCategoryDataset rendDs = new DefaultCategoryDataset();
        for (VtaMensal v : vtas) {
            double rMensal = rendSvc.calcularR(v) - somaAntByVtaId.getOrDefault(v.getId(), 0.0);
            rendDs.addValue(rMensal, "Rendimento", FormatUtil.mesAno(v.getPeriodoMes(), v.getPeriodoAno()));
        }

        org.jfree.chart.renderer.category.LineAndShapeRenderer rendRend =
            new org.jfree.chart.renderer.category.LineAndShapeRenderer() {
                @Override public java.awt.Paint getItemPaint(int row, int col) { return textMut; }
                @Override public java.awt.Paint getItemFillPaint(int row, int col) {
                    Number val = rendDs.getValue(row, col);
                    return (val != null && val.doubleValue() >= 0) ? green : red;
                }
            };
        rendRend.setDefaultLinesVisible(true); rendRend.setDefaultShapesVisible(true);
        rendRend.setDefaultShapesFilled(true); rendRend.setUseFillPaint(true);
        rendRend.setDefaultShape(dot); rendRend.setDefaultStroke(stroke);

        org.jfree.chart.axis.CategoryAxis xR = new org.jfree.chart.axis.CategoryAxis(null);
        xR.setTickLabelPaint(textMut); xR.setTickLabelFont(fontXs);
        xR.setAxisLinePaint(border); xR.setTickMarkPaint(border);
        org.jfree.chart.axis.NumberAxis yR = new org.jfree.chart.axis.NumberAxis("R$");
        yR.setTickLabelPaint(textMut); yR.setTickLabelFont(fontXs);
        yR.setAxisLinePaint(border); yR.setTickMarkPaint(border);

        org.jfree.chart.plot.CategoryPlot rendPlot = new org.jfree.chart.plot.CategoryPlot(rendDs, xR, yR, rendRend);
        rendPlot.setBackgroundPaint(bgCard); rendPlot.setOutlinePaint(border);
        rendPlot.setRangeGridlinePaint(border); rendPlot.setDomainGridlinesVisible(false);
        rendPlot.setRangeZeroBaselinePaint(textMut); rendPlot.setRangeZeroBaselineVisible(true);

        String rendTitle = (anoSelecionado != ANO_TODOS)
                ? "Rendimento Mensal — " + anoSelecionado
                : "Rendimento Mensal — Total";
        JFreeChart rendChart = new JFreeChart(rendTitle, fontBold, rendPlot, false);
        rendChart.setBackgroundPaint(bgCard); rendChart.setBorderVisible(false);
        rendChart.getTitle().setPaint(textMut); rendChart.getTitle().setFont(fontBold);

        SwingNode rendNode = new SwingNode();
        SwingUtilities.invokeLater(() -> {
            ChartPanel cp = new ChartPanel(rendChart); cp.setBackground(bgCard); rendNode.setContent(cp);
        });
        StackPane rendPane = new StackPane(rendNode); rendPane.setPrefSize(400, 220);

        VBox saldoBox = new VBox(saldoPane);
        saldoBox.getStyleClass().add("card"); saldoBox.setPadding(Insets.EMPTY);
        HBox.setHgrow(saldoBox, Priority.ALWAYS);

        VBox rendBox = new VBox(rendPane);
        rendBox.getStyleClass().add("card"); rendBox.setPadding(Insets.EMPTY);
        HBox.setHgrow(rendBox, Priority.ALWAYS);

        return new HBox(12, saldoBox, rendBox);
    }

    private Label colHeader(String sigla) {
        Label lbl = new Label(sigla);
        lbl.setStyle("-fx-text-fill: #7d8fa0; -fx-font-size: 15px; -fx-font-weight: bold;");
        GlossarioTooltip.aplicar(lbl, sigla);
        return lbl;
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
