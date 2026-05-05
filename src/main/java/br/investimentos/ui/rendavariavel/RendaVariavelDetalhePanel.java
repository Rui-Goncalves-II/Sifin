package br.investimentos.ui.rendavariavel;

import br.investimentos.model.AporteRv;
import br.investimentos.model.Investimento;
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
        btnVac.setOnAction(e -> { new VacFormDialog(inv, vacRepo, cotacaoSvc).showAndWait(); construir(); });
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

        // Operações table
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
        opTable.getItems().addAll(aporteRepo.findByInvestimento(inv.getId()));

        content.getChildren().addAll(grid, opTitle, opTable);
        return content;
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
