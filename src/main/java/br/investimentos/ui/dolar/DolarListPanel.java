package br.investimentos.ui.dolar;

import br.investimentos.model.CotacaoDolar;
import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.*;
import br.investimentos.service.ConsolidacaoService;
import br.investimentos.service.CotacaoService;
import br.investimentos.ui.util.FormatUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;

public class DolarListPanel extends BorderPane {

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final ConsolidacaoService consolSvc;
    private final CotacaoService cotacaoSvc;
    private final Consumer<Node> navigate;

    private TableView<Investimento> table;

    public DolarListPanel(InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
                           ConsolidacaoService consolSvc, CotacaoService cotacaoSvc, Consumer<Node> navigate) {
        this.invRepo = invRepo; this.movRepo = movRepo;
        this.consolSvc = consolSvc; this.cotacaoSvc = cotacaoSvc; this.navigate = navigate;
        construir();
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("💵 Dólar");
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        double cotCompra = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorCompra).orElse(0.0);
        double cotVenda  = cotacaoSvc.getCotacaoAtual().map(CotacaoDolar::getValorVenda).orElse(0.0);
        String cotText = cotCompra > 0
                ? "Compra: R$ " + FormatUtil.numero(cotCompra, 4) + "  |  Venda: R$ " + FormatUtil.numero(cotVenda, 4)
                : "—";
        Label cotLabel = new Label(cotText);
        cotLabel.getStyleClass().add("usd-badge");

        Button btnNovo = new Button("+ Novo");
        btnNovo.getStyleClass().add("btn-primary");
        btnNovo.setOnAction(e -> { new DolarFormDialog(null, invRepo).showAndWait(); refresh(); });
        header.getChildren().addAll(title, spacer, cotLabel, btnNovo);
        setTop(header);

        table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Investimento, String> colNome = new TableColumn<>("Nome");
        colNome.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNome()));
        colNome.setPrefWidth(200);

        TableColumn<Investimento, String> colUsd = new TableColumn<>("Saldo USD");
        colUsd.setCellValueFactory(c -> {
            double usd = consolSvc.calcularSaldoUsd(c.getValue().getId());
            return new javafx.beans.property.SimpleStringProperty("$ " + FormatUtil.numero(usd, 2));
        });
        colUsd.setPrefWidth(120);

        TableColumn<Investimento, String> colBrl = new TableColumn<>("BRL (compra)");
        colBrl.setCellValueFactory(c -> {
            double usd = consolSvc.calcularSaldoUsd(c.getValue().getId());
            return new javafx.beans.property.SimpleStringProperty(
                    cotCompra > 0 ? FormatUtil.brl(usd * cotCompra) : "—");
        });
        colBrl.setPrefWidth(130);

        TableColumn<Investimento, String> colBrlVenda = new TableColumn<>("BRL (venda)");
        colBrlVenda.setCellValueFactory(c -> {
            double usd = consolSvc.calcularSaldoUsd(c.getValue().getId());
            return new javafx.beans.property.SimpleStringProperty(
                    cotVenda > 0 ? FormatUtil.brl(usd * cotVenda) : "—");
        });
        colBrlVenda.setPrefWidth(130);

        TableColumn<Investimento, Void> colAcoes = new TableColumn<>("Ações");
        colAcoes.setMinWidth(60);
        colAcoes.setPrefWidth(60);
        colAcoes.setMaxWidth(60);
        colAcoes.setCellFactory(tc -> new TableCell<>() {
            final Button btnEdit = new Button("✏");
            final HBox box = new HBox(4, btnEdit);
            {
                btnEdit.getStyleClass().add("btn-icon");
                btnEdit.setOnAction(e -> {
                    new DolarFormDialog(getTableView().getItems().get(getIndex()), invRepo).showAndWait();
                    refresh();
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().addAll(colNome, colUsd, colBrl, colBrlVenda, colAcoes);
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                Investimento inv = table.getSelectionModel().getSelectedItem();
                navigate.accept(new DolarDetalhePanel(inv, invRepo, movRepo, consolSvc, cotacaoSvc, navigate));
            }
        });

        VBox content = new VBox(table);
        content.setPadding(new Insets(20, 24, 24, 24));
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
        refresh();
    }

    private void refresh() {
        table.getItems().setAll(invRepo.findByTipo(TipoInvestimento.DOLAR));
    }
}
