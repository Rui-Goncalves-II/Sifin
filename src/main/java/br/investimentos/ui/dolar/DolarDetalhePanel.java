package br.investimentos.ui.dolar;

import br.investimentos.model.CotacaoDolar;
import br.investimentos.model.Investimento;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.repository.InvestimentoRepository;
import br.investimentos.repository.MovimentacaoRepository;
import br.investimentos.service.ConsolidacaoService;
import br.investimentos.service.CotacaoService;
import br.investimentos.ui.util.FormatUtil;
import br.investimentos.ui.util.GlossarioTooltip;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import javafx.util.StringConverter;

public class DolarDetalhePanel extends BorderPane {

    private static final int ANO_TODOS = -1;

    private final Investimento inv;
    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final ConsolidacaoService consolSvc;
    private final CotacaoService cotacaoSvc;
    private final Consumer<Node> navigate;

    private int anoSelecionado;

    public DolarDetalhePanel(Investimento inv, InvestimentoRepository invRepo,
                              MovimentacaoRepository movRepo, ConsolidacaoService consolSvc,
                              CotacaoService cotacaoSvc, Consumer<Node> navigate) {
        this.inv = inv; this.invRepo = invRepo; this.movRepo = movRepo;
        this.consolSvc = consolSvc; this.cotacaoSvc = cotacaoSvc; this.navigate = navigate;
        anoSelecionado = LocalDate.now().getYear();
        construir();
    }

    private List<Integer> anosComDados() {
        TreeSet<Integer> anos = new TreeSet<>();
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
        btnVoltar.setOnAction(e -> navigate.accept(new DolarListPanel(invRepo, movRepo, consolSvc, cotacaoSvc, navigate)));
        Label title = new Label("💵 " + inv.getNome());
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnMov = new Button("+ Movimentação");
        btnMov.getStyleClass().add("btn-primary");
        btnMov.setOnAction(e -> { new DolarMovFormDialog(inv, new Movimentacao(), movRepo).showAndWait(); construir(); });
        header.getChildren().addAll(btnVoltar, title, spacer, buildYearCombo(), btnMov);
        setTop(header);

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    private VBox buildContent() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 24, 24));

        // Saldo all-time (posição atual)
        List<Movimentacao> todasMovs = movRepo.findByInvestimento(inv.getId());
        double totalUsd = todasMovs.stream()
                .mapToDouble(m -> m.getTipoMov() == TipoMovimentacao.DEPOSITO ? m.getValor() : -m.getValor())
                .sum();
        double cotVenda  = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorVenda).orElse(0.0);
        double cotCompra = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorCompra).orElse(0.0);
        double brlCompra = totalUsd * cotCompra;
        double brlVenda  = totalUsd * cotVenda;

        // CMC = Σ(usd × cotação) / Σ(usd) — apenas depósitos com cotação registrada, sempre all-time
        double totalBrlPago = 0, totalUsdComprado = 0;
        for (Movimentacao m : todasMovs) {
            if (m.getTipoMov() == TipoMovimentacao.DEPOSITO && m.getCotacaoDolar() != null) {
                totalBrlPago    += m.getValor() * m.getCotacaoDolar();
                totalUsdComprado += m.getValor();
            }
        }
        double cmc = totalUsdComprado > 0 ? totalBrlPago / totalUsdComprado : 0;
        String cmcStr = cmc > 0 ? "R$ " + FormatUtil.numero(cmc, 4) : "—";

        // Custo Total BRL = Σ(deposito × cotacao) - Σ(saque × cotacao), com fallback cotCompra
        double custoTotalBrl = 0;
        for (Movimentacao m : todasMovs) {
            double cot = m.getCotacaoDolar() != null ? m.getCotacaoDolar() : cotCompra;
            if (m.getTipoMov() == TipoMovimentacao.DEPOSITO) custoTotalBrl += m.getValor() * cot;
            else                                              custoTotalBrl -= m.getValor() * cot;
        }
        double lucroCambial = (cotVenda > 0 ? brlVenda : brlCompra) - custoTotalBrl;
        double rentValue = 0;
        String rentStr;
        if (custoTotalBrl == 0) {
            rentStr = "—";
        } else {
            rentValue = (lucroCambial / Math.abs(custoTotalBrl)) * 100;
            rentStr = FormatUtil.pct(rentValue);
        }
        String lucroStyle = lucroCambial >= 0 ? "positive" : "negative";
        String rentStyle  = rentStr.equals("—") ? "neutral" : (rentValue >= 0 ? "positive" : "negative");

        String usdStr = "$ " + FormatUtil.numero(totalUsd, 2);
        String brlC   = cotCompra > 0 ? FormatUtil.brl(brlCompra) : "—";
        String brlV   = cotVenda  > 0 ? FormatUtil.brl(brlVenda)  : "—";

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints(); cc.setPercentWidth(25); cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        if (anoSelecionado != ANO_TODOS) {
            double aportadoAno = movRepo.findByInvestimentoEAno(inv.getId(), anoSelecionado).stream()
                    .mapToDouble(m -> m.getTipoMov() == TipoMovimentacao.DEPOSITO ? m.getValor() : -m.getValor())
                    .sum();
            // Linha 0
            grid.add(metricCard("Saldo Total (USD)", usdStr, "neutral"), 0, 0);
            grid.add(metricCard("Aportado " + anoSelecionado + " (USD)", "$ " + FormatUtil.numero(aportadoAno, 2), "neutral"), 1, 0);
            grid.add(metricCardDual("Valor (compra)", usdStr, brlC, "neutral"), 2, 0);
            grid.add(metricCardDual("Valor (venda)",  usdStr, brlV, "neutral"), 3, 0);
            // Linha 1
            grid.add(metricCard("CMC", cmcStr, "neutral"), 0, 1);
            grid.add(metricCard("Custo Total BRL", FormatUtil.brl(custoTotalBrl), "neutral"), 1, 1);
            grid.add(metricCard("Lucro Cambial", FormatUtil.brl(lucroCambial), lucroStyle), 2, 1);
            grid.add(metricCard("Rentabilidade %", rentStr, rentStyle), 3, 1);
        } else {
            // Linha 0
            grid.add(metricCard("Saldo Total (USD)", usdStr, "neutral"), 0, 0);
            grid.add(metricCard("CMC", cmcStr, "neutral"), 1, 0);
            grid.add(metricCardDual("Valor (compra)", usdStr, brlC, "neutral"), 2, 0);
            grid.add(metricCardDual("Valor (venda)",  usdStr, brlV, "neutral"), 3, 0);
            // Linha 1
            grid.add(metricCard("Custo Total BRL", FormatUtil.brl(custoTotalBrl), "neutral"), 0, 1);
            grid.add(metricCard("Lucro Cambial", FormatUtil.brl(lucroCambial), lucroStyle), 1, 1);
            grid.add(metricCard("Rentabilidade %", rentStr, rentStyle), 2, 1);
        }

        // Movimentações filtradas
        String movLabel = (anoSelecionado != ANO_TODOS)
                ? "Movimentações — " + anoSelecionado
                : "Movimentações — Total";
        Label movTitle = new Label(movLabel);
        movTitle.getStyleClass().add("section-title");

        TableView<Movimentacao> movTable = new TableView<>();
        movTable.getStyleClass().add("table-view");
        movTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        movTable.setPrefHeight(300);

        TableColumn<Movimentacao, String> cPer = new TableColumn<>("Período");
        cPer.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().getPeriodoMes(), c.getValue().getPeriodoAno())));
        cPer.setPrefWidth(90);

        TableColumn<Movimentacao, String> cTipo = new TableColumn<>("Tipo");
        cTipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTipoMov().label()));
        cTipo.setPrefWidth(90);

        TableColumn<Movimentacao, String> cValor = new TableColumn<>("Valor (USD)");
        cValor.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                "$ " + FormatUtil.numero(c.getValue().getValor(), 2)));
        cValor.setPrefWidth(120);
        cValor.setCellFactory(col -> new TableCell<>() {
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

        TableColumn<Movimentacao, String> cCot = new TableColumn<>("Cotação R$");
        cCot.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getCotacaoDolar() != null ? FormatUtil.numero(c.getValue().getCotacaoDolar(), 4) : "—"));
        cCot.setPrefWidth(110);

        TableColumn<Movimentacao, String> cBrl = new TableColumn<>("Valor BRL");
        cBrl.setCellValueFactory(c -> {
            Movimentacao m = c.getValue();
            double brl = m.getCotacaoDolar() != null ? m.getValor() * m.getCotacaoDolar() : 0;
            return new javafx.beans.property.SimpleStringProperty(brl > 0 ? FormatUtil.brl(brl) : "—");
        });
        cBrl.setPrefWidth(120);
        cBrl.setCellFactory(col -> new TableCell<>() {
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

        TableColumn<Movimentacao, Void> cAcoes = new TableColumn<>("Ações");
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
                    Movimentacao m = getTableView().getItems().get(getIndex());
                    new DolarMovFormDialog(inv, m, movRepo).showAndWait();
                    construir();
                });
                del.setOnAction(e -> {
                    Movimentacao m = getTableView().getItems().get(getIndex());
                    Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Remover movimentação?", ButtonType.YES, ButtonType.NO);
                    conf.showAndWait().filter(r -> r == ButtonType.YES).ifPresent(r -> { movRepo.deletar(m.getId()); construir(); });
                });
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : new HBox(6, edit, del)); }
        });

        movTable.getColumns().addAll(cPer, cTipo, cValor, cCot, cBrl, cAcoes);
        List<Movimentacao> movs = (anoSelecionado != ANO_TODOS)
                ? movRepo.findByInvestimentoEAno(inv.getId(), anoSelecionado)
                : new ArrayList<>(todasMovs);
        Collections.reverse(movs);
        movTable.getItems().addAll(movs);

        content.getChildren().addAll(grid, movTitle, movTable);
        return content;
    }

    private VBox metricCard(String title, String value, String style) {
        VBox card = new VBox(4); card.getStyleClass().add("card");
        Label t = new Label(title); t.getStyleClass().add("card-title");
        GlossarioTooltip.aplicar(t, title);
        Label v = new Label(value); v.getStyleClass().addAll("card-value-sm", style);
        card.getChildren().addAll(t, v);
        return card;
    }

    private VBox metricCardDual(String title, String valueUsd, String valueBrl, String style) {
        VBox card = new VBox(4); card.getStyleClass().add("card");
        Label t = new Label(title); t.getStyleClass().add("card-title");
        GlossarioTooltip.aplicar(t, title);
        Label vUsd = new Label(valueUsd); vUsd.getStyleClass().addAll("card-value-sm", style);
        Label vBrl = new Label(valueBrl); vBrl.getStyleClass().addAll("card-value-sm", style);
        card.getChildren().addAll(t, vUsd, vBrl);
        return card;
    }
}
