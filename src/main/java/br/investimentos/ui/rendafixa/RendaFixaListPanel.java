package br.investimentos.ui.rendafixa;

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

import java.util.List;
import java.util.function.Consumer;

public class RendaFixaListPanel extends BorderPane {

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final VtaMensalRepository vtaRepo;
    private final VaiAnualRepository vaiRepo;
    private final RendimentoService rendSvc;
    private final TaxaService taxaSvc;
    private final SaldoService saldoSvc;
    private final Consumer<Node> navigate;

    private TableView<Investimento> table;

    public RendaFixaListPanel(InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
                               VtaMensalRepository vtaRepo, VaiAnualRepository vaiRepo,
                               RendimentoService rendSvc, TaxaService taxaSvc,
                               SaldoService saldoSvc, Consumer<Node> navigate) {
        this.invRepo = invRepo; this.movRepo = movRepo; this.vtaRepo = vtaRepo;
        this.vaiRepo = vaiRepo; this.rendSvc = rendSvc; this.taxaSvc = taxaSvc;
        this.saldoSvc = saldoSvc; this.navigate = navigate;
        construir();
    }

    private void construir() {
        // Header
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("🏦 Renda Fixa");
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnNovo = new Button("+ Novo Ativo");
        btnNovo.getStyleClass().add("btn-primary");
        btnNovo.setOnAction(e -> abrirForm(null));
        header.getChildren().addAll(title, spacer, btnNovo);
        setTop(header);

        // Table
        table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Investimento, String> colNome = new TableColumn<>("Nome");
        colNome.setMinWidth(headerW("Nome"));
        colNome.setPrefWidth(220);
        colNome.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNome()));

        TableColumn<Investimento, String> colSub = col("Subtipo", 90);
        colSub.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getSubtipo() != null ? c.getValue().getSubtipo() : "—"));

        TableColumn<Investimento, String> colIdx = col("Indexador", 90);
        colIdx.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getIndexador() != null ? c.getValue().getIndexador() : "—"));

        TableColumn<Investimento, String> colTaxa = col("Taxa a.a.", 90);
        colTaxa.setCellValueFactory(c -> {
            Double t = c.getValue().getTaxaAnual();
            return new javafx.beans.property.SimpleStringProperty(t != null ? FormatUtil.pct(t) : "calc.");
        });

        TableColumn<Investimento, String> colSaldo = col("Saldo Atual", 140);
        colSaldo.setCellValueFactory(c -> {
            double s = saldoSvc.saldoAtual(c.getValue(), 0);
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(s));
        });

        TableColumn<Investimento, String> colVenc = col("Vencimento", 100);
        colVenc.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getDataVencimento() != null ? c.getValue().getDataVencimento() : "—"));

        TableColumn<Investimento, Void> colAcoes = new TableColumn<>("Ações");
        double acoesW = Math.max(140, headerW("Ações"));
        colAcoes.setMinWidth(acoesW);
        colAcoes.setPrefWidth(acoesW);
        colAcoes.setMaxWidth(acoesW);
        colAcoes.setCellFactory(tc -> new TableCell<>() {
            final Button btnVer = new Button("👁");
            final Button btnVta = new Button("VTA");
            final Button btnEdit = new Button("✏");
            final HBox box = new HBox(4, btnVer, btnVta, btnEdit);
            {
                btnVer.getStyleClass().add("btn-icon");
                btnVta.getStyleClass().addAll("btn-secondary");
                btnVta.setStyle("-fx-padding: 4 8; -fx-font-size: 11px;");
                btnEdit.getStyleClass().add("btn-icon");
                btnVer.setOnAction(e -> navigate.accept(new RendaFixaDetalhePanel(
                        getTableView().getItems().get(getIndex()), invRepo, movRepo, vtaRepo,
                        vaiRepo, rendSvc, taxaSvc, saldoSvc, navigate)));
                btnVta.setOnAction(e -> {
                    new VtaFormDialog(getTableView().getItems().get(getIndex()), vtaRepo, vaiRepo, rendSvc).showAndWait();
                    refresh();
                });
                btnEdit.setOnAction(e -> abrirForm(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().addAll(colNome, colSub, colIdx, colTaxa, colSaldo, colVenc, colAcoes);

        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                Investimento inv = table.getSelectionModel().getSelectedItem();
                navigate.accept(new RendaFixaDetalhePanel(inv, invRepo, movRepo, vtaRepo, vaiRepo, rendSvc, taxaSvc, saldoSvc, navigate));
            }
        });

        VBox content = new VBox(table);
        content.setPadding(new Insets(20, 24, 24, 24));
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);

        refresh();
    }

    private <T> TableColumn<Investimento, T> col(String title, double contentMin) {
        TableColumn<Investimento, T> c = new TableColumn<>(title);
        double w = Math.max(contentMin, headerW(title));
        c.setMinWidth(w);
        c.setPrefWidth(w);
        return c;
    }

    private static double headerW(String title) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(title);
        t.setFont(javafx.scene.text.Font.font("SansSerif", javafx.scene.text.FontWeight.BOLD, 11));
        return Math.ceil(t.getBoundsInLocal().getWidth()) + 32;
    }

    private void refresh() {
        table.getItems().setAll(invRepo.findByTipo(TipoInvestimento.RENDA_FIXA));
    }

    private void abrirForm(Investimento inv) {
        new RendaFixaFormDialog(inv, invRepo).showAndWait();
        refresh();
    }
}
