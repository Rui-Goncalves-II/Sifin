package br.investimentos.ui.projecao;

import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.InvestimentoRepository;
import br.investimentos.service.ProjecaoService;
import br.investimentos.ui.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;

public class ProjecaoPanel extends BorderPane {

    private final InvestimentoRepository invRepo;
    private final ProjecaoService projecaoSvc;
    private final Consumer<Node> navigate;

    private ComboBox<Investimento> cbInvestimento;
    private TextField tfAporte;
    private Spinner<Integer> spMeses;
    private TableView<ProjecaoService.PontoProjecao> tabela;

    public ProjecaoPanel(InvestimentoRepository invRepo, ProjecaoService projecaoSvc, Consumer<Node> navigate) {
        this.invRepo = invRepo;
        this.projecaoSvc = projecaoSvc;
        this.navigate = navigate;
        construir();
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("📈 Projeção");
        title.getStyleClass().add("page-title");
        header.getChildren().add(title);
        setTop(header);

        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 24, 24));

        // Controls card
        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_LEFT);

        cbInvestimento = new ComboBox<>();
        cbInvestimento.getItems().addAll(invRepo.findByTipo(TipoInvestimento.RENDA_FIXA));
        cbInvestimento.setPromptText("Selecione o investimento...");
        cbInvestimento.setPrefWidth(260);

        tfAporte = new TextField("0");
        tfAporte.setPrefWidth(130);
        tfAporte.setPromptText("Aporte mensal (R$)");

        spMeses = new Spinner<>(1, 120, 12);
        spMeses.setPrefWidth(90);
        spMeses.setEditable(true);

        Button btnProjetar = new Button("Projetar");
        btnProjetar.getStyleClass().add("btn-primary");
        btnProjetar.setOnAction(e -> projetar());

        controls.getChildren().addAll(
                new Label("Ativo:"), cbInvestimento,
                new Label("Aporte:"), tfAporte,
                new Label("Meses:"), spMeses,
                btnProjetar);

        VBox controlCard = new VBox(controls);
        controlCard.getStyleClass().add("card");

        tabela = buildTabela();
        Label tabelaTitle = new Label("Cenários de Projeção");
        tabelaTitle.getStyleClass().add("section-title");

        content.getChildren().addAll(controlCard, tabelaTitle, tabela);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    private TableView<ProjecaoService.PontoProjecao> buildTabela() {
        TableView<ProjecaoService.PontoProjecao> t = new TableView<>();
        t.getStyleClass().add("table-view");

        TableColumn<ProjecaoService.PontoProjecao, String> cPer = new TableColumn<>("Período");
        cPer.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().mes(), c.getValue().ano())));
        cPer.setPrefWidth(100);

        TableColumn<ProjecaoService.PontoProjecao, String> cPess = new TableColumn<>("Pessimista (−20%)");
        cPess.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.brl(c.getValue().pessimista())));
        cPess.setPrefWidth(170);
        cPess.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<ProjecaoService.PontoProjecao, String> cReal = new TableColumn<>("Realista");
        cReal.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.brl(c.getValue().realista())));
        cReal.setPrefWidth(160);
        cReal.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<ProjecaoService.PontoProjecao, String> cOtim = new TableColumn<>("Otimista (+20%)");
        cOtim.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.brl(c.getValue().otimista())));
        cOtim.setPrefWidth(160);
        cOtim.setStyle("-fx-alignment: CENTER-RIGHT;");

        t.getColumns().addAll(cPer, cPess, cReal, cOtim);
        return t;
    }

    private void projetar() {
        Investimento inv = cbInvestimento.getValue();
        if (inv == null) {
            new Alert(Alert.AlertType.WARNING, "Selecione um investimento.").showAndWait();
            return;
        }
        double aporte;
        try {
            aporte = Double.parseDouble(tfAporte.getText().replace(",", ".").trim());
        } catch (NumberFormatException ex) {
            new Alert(Alert.AlertType.WARNING, "Valor de aporte inválido.").showAndWait();
            return;
        }
        int meses = spMeses.getValue();
        List<ProjecaoService.PontoProjecao> pontos = projecaoSvc.projetar(inv, aporte, meses);
        tabela.getItems().setAll(pontos);
    }
}
