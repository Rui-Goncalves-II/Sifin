package br.investimentos.ui.rendavariavel;

import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.ui.util.FormatUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;

public class RendaVariavelListPanel extends BorderPane {

    private final InvestimentoRepository invRepo;
    private final AporteRvRepository aporteRepo;
    private final VacMensalRepository vacRepo;
    private final RendaVariavelService rvSvc;
    private final CotacaoService cotacaoSvc;
    private final Consumer<Node> navigate;

    private TableView<Investimento> table;
    private String filtroSub = null;

    public RendaVariavelListPanel(InvestimentoRepository invRepo, AporteRvRepository aporteRepo,
                                   VacMensalRepository vacRepo, RendaVariavelService rvSvc,
                                   CotacaoService cotacaoSvc, Consumer<Node> navigate) {
        this.invRepo = invRepo; this.aporteRepo = aporteRepo; this.vacRepo = vacRepo;
        this.rvSvc = rvSvc; this.cotacaoSvc = cotacaoSvc; this.navigate = navigate;
        construir();
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("💹 Renda Variável");
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        // Subtipo filter
        ComboBox<String> filtro = new ComboBox<>();
        filtro.getItems().addAll("Todos", "FII", "ACAO", "ETF");
        filtro.setValue("Todos");
        filtro.setOnAction(e -> {
            filtroSub = "Todos".equals(filtro.getValue()) ? null : filtro.getValue();
            refresh();
        });

        Button btnNovo = new Button("+ Novo Ativo");
        btnNovo.getStyleClass().add("btn-primary");
        btnNovo.setOnAction(e -> { new RendaVariavelFormDialog(null, invRepo).showAndWait(); refresh(); });
        header.getChildren().addAll(title, spacer, new Label("Filtrar:"), filtro, btnNovo);
        setTop(header);

        table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Investimento, String> colNome = new TableColumn<>("Nome");
        colNome.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNome()));
        colNome.setPrefWidth(180);

        TableColumn<Investimento, String> colSub = new TableColumn<>("Subtipo");
        colSub.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getSubtipo() != null ? c.getValue().getSubtipo() : "—"));
        colSub.setPrefWidth(70);

        TableColumn<Investimento, String> colVac = new TableColumn<>("VAC Atual");
        colVac.setCellValueFactory(c -> {
            var pos = rvSvc.calcular(c.getValue().getId());
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(pos.vac()));
        });
        colVac.setPrefWidth(110);

        TableColumn<Investimento, String> colQc = new TableColumn<>("QC");
        colQc.setCellValueFactory(c -> {
            var pos = rvSvc.calcular(c.getValue().getId());
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.qtd(pos.qc()));
        });
        colQc.setPrefWidth(80);

        TableColumn<Investimento, String> colPtg = new TableColumn<>("PTG");
        colPtg.setCellValueFactory(c -> {
            var pos = rvSvc.calcular(c.getValue().getId());
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(pos.ptg()));
        });
        colPtg.setPrefWidth(120);

        TableColumn<Investimento, String> colLeb = new TableColumn<>("LEB");
        colLeb.setCellValueFactory(c -> {
            var pos = rvSvc.calcular(c.getValue().getId());
            return new javafx.beans.property.SimpleStringProperty(
                    FormatUtil.brl(pos.leb()) + " (" + FormatUtil.pct(pos.lebPct()) + ")");
        });
        colLeb.setPrefWidth(160);

        TableColumn<Investimento, Void> colAcoes = new TableColumn<>("Ações");
        colAcoes.setPrefWidth(120);
        colAcoes.setCellFactory(tc -> new TableCell<>() {
            final Button btnVer = new Button("👁");
            final Button btnOp = new Button("+ Op.");
            final HBox box = new HBox(4, btnVer, btnOp);
            {
                btnVer.getStyleClass().add("btn-icon");
                btnOp.getStyleClass().add("btn-secondary");
                btnOp.setStyle("-fx-padding: 4 8; -fx-font-size: 11px;");
                btnVer.setOnAction(e -> navigate.accept(new RendaVariavelDetalhePanel(
                        getTableView().getItems().get(getIndex()), invRepo, aporteRepo, vacRepo, rvSvc, cotacaoSvc, navigate)));
                btnOp.setOnAction(e -> {
                    new RendaVariavelFormDialog(getTableView().getItems().get(getIndex()), invRepo).showAndWait();
                    // para operações (compra/venda/dividendo) abre o detalhe
                    navigate.accept(new RendaVariavelDetalhePanel(
                            getTableView().getItems().get(getIndex()), invRepo, aporteRepo, vacRepo, rvSvc, cotacaoSvc, navigate));
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().addAll(colNome, colSub, colVac, colQc, colPtg, colLeb, colAcoes);
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                Investimento inv = table.getSelectionModel().getSelectedItem();
                navigate.accept(new RendaVariavelDetalhePanel(inv, invRepo, aporteRepo, vacRepo, rvSvc, cotacaoSvc, navigate));
            }
        });

        VBox content = new VBox(table);
        content.setPadding(new Insets(20, 24, 24, 24));
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
        refresh();
    }

    private void refresh() {
        var all = invRepo.findByTipo(TipoInvestimento.RENDA_VARIAVEL);
        if (filtroSub != null) all = all.stream().filter(i -> filtroSub.equals(i.getSubtipo())).toList();
        table.getItems().setAll(all);
    }
}
