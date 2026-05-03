package br.investimentos.ui.dolar;

import br.investimentos.model.CotacaoDolar;
import br.investimentos.model.Investimento;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.repository.InvestimentoRepository;
import br.investimentos.repository.MovimentacaoRepository;
import br.investimentos.service.CotacaoService;
import br.investimentos.ui.util.FormatUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.function.Consumer;

public class DolarDetalhePanel extends BorderPane {

    private final Investimento inv;
    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final CotacaoService cotacaoSvc;
    private final Consumer<Node> navigate;

    public DolarDetalhePanel(Investimento inv, InvestimentoRepository invRepo,
                              MovimentacaoRepository movRepo, CotacaoService cotacaoSvc,
                              Consumer<Node> navigate) {
        this.inv = inv; this.invRepo = invRepo; this.movRepo = movRepo;
        this.cotacaoSvc = cotacaoSvc; this.navigate = navigate;
        construir();
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Button btnVoltar = new Button("← Voltar");
        btnVoltar.getStyleClass().add("btn-secondary");
        btnVoltar.setOnAction(e -> navigate.accept(new DolarListPanel(invRepo, movRepo, cotacaoSvc, navigate)));
        Label title = new Label("💵 " + inv.getNome());
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnMov = new Button("+ Movimentação");
        btnMov.getStyleClass().add("btn-primary");
        btnMov.setOnAction(e -> { new DolarMovFormDialog(inv, movRepo).showAndWait(); construir(); });
        header.getChildren().addAll(btnVoltar, title, spacer, btnMov);
        setTop(header);

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    private VBox buildContent() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 24, 24));

        double totalUsd = movRepo.findByInvestimento(inv.getId()).stream()
                .mapToDouble(m -> m.getTipoMov() == TipoMovimentacao.DEPOSITO ? m.getValor() : -m.getValor())
                .sum();
        double cotCompra = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorCompra).orElse(0.0);
        double cotVenda = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorVenda).orElse(0.0);
        double brlCompra = totalUsd * cotCompra;
        double brlVenda = totalUsd * cotVenda;

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        ColumnConstraints cc = new ColumnConstraints(); cc.setPercentWidth(25); cc.setHgrow(Priority.ALWAYS);
        for (int i = 0; i < 4; i++) grid.getColumnConstraints().add(cc);

        grid.add(metricCard("Saldo (USD)", "$ " + FormatUtil.numero(totalUsd, 4), "neutral"), 0, 0);
        grid.add(metricCard("Cotação Compra", "R$ " + FormatUtil.numero(cotCompra, 4), "neutral"), 1, 0);
        grid.add(metricCard("Valor (compra)", FormatUtil.brl(brlCompra), "neutral"), 2, 0);
        grid.add(metricCard("Valor (venda)", FormatUtil.brl(brlVenda), "neutral"), 3, 0);

        // Movimentações
        Label movTitle = new Label("Movimentações");
        movTitle.getStyleClass().add("section-title");

        TableView<Movimentacao> movTable = new TableView<>();
        movTable.getStyleClass().add("table-view");
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
                "$ " + FormatUtil.numero(c.getValue().getValor(), 4)));
        cValor.setPrefWidth(120);

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

        TableColumn<Movimentacao, Void> cAcoes = new TableColumn<>("Ações");
        cAcoes.setPrefWidth(70);
        cAcoes.setCellFactory(tc -> new TableCell<>() {
            final Button del = new Button("🗑");
            { del.getStyleClass().add("btn-icon"); del.setStyle("-fx-text-fill: #c62828;");
              del.setOnAction(e -> {
                  Movimentacao m = getTableView().getItems().get(getIndex());
                  Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Remover movimentação?", ButtonType.YES, ButtonType.NO);
                  conf.showAndWait().filter(r -> r == ButtonType.YES).ifPresent(r -> { movRepo.deletar(m.getId()); construir(); });
              });
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : del); }
        });

        movTable.getColumns().addAll(cPer, cTipo, cValor, cCot, cBrl, cAcoes);
        movTable.getItems().addAll(movRepo.findByInvestimento(inv.getId()));

        content.getChildren().addAll(grid, movTitle, movTable);
        return content;
    }

    private VBox metricCard(String title, String value, String style) {
        VBox card = new VBox(4); card.getStyleClass().add("card");
        Label t = new Label(title); t.getStyleClass().add("card-title");
        Label v = new Label(value); v.getStyleClass().addAll("card-value-sm", style);
        card.getChildren().addAll(t, v);
        return card;
    }
}
