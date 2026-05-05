package br.investimentos.ui.transacao;

import br.investimentos.model.AporteRv;
import br.investimentos.model.Investimento;
import br.investimentos.model.Movimentacao;
import br.investimentos.repository.AporteRvRepository;
import br.investimentos.repository.InvestimentoRepository;
import br.investimentos.repository.MovimentacaoRepository;
import br.investimentos.ui.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public class TransacaoPanel extends BorderPane {

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final AporteRvRepository aporteRepo;

    record LinhaTransacao(int periodoMes, int periodoAno, String ativo, String tipo, double valor, String notas) {}

    public TransacaoPanel(InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
                          AporteRvRepository aporteRepo) {
        this.invRepo = invRepo;
        this.movRepo = movRepo;
        this.aporteRepo = aporteRepo;
        construir();
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("🔄 Transações");
        title.getStyleClass().add("page-title");
        header.getChildren().add(title);
        setTop(header);

        TableView<LinhaTransacao> table = buildTable();

        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 24, 24));
        content.getChildren().add(table);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);

        popularTabela(table);
    }

    private TableView<LinhaTransacao> buildTable() {
        TableView<LinhaTransacao> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<LinhaTransacao, String> cPer = new TableColumn<>("Período");
        cPer.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().periodoMes(), c.getValue().periodoAno())));
        cPer.setPrefWidth(100);

        TableColumn<LinhaTransacao, String> cAtivo = new TableColumn<>("Ativo");
        cAtivo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().ativo()));
        cAtivo.setPrefWidth(220);

        TableColumn<LinhaTransacao, String> cTipo = new TableColumn<>("Tipo");
        cTipo.setCellValueFactory(c -> new SimpleStringProperty(labelTipo(c.getValue().tipo())));
        cTipo.setPrefWidth(140);

        TableColumn<LinhaTransacao, String> cValor = new TableColumn<>("Valor");
        cValor.setCellValueFactory(c -> new SimpleStringProperty(FormatUtil.brl(c.getValue().valor())));
        cValor.setPrefWidth(140);
        cValor.setStyle("-fx-alignment: CENTER-RIGHT;");
        cValor.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                LinhaTransacao linha = getTableRow().getItem();
                boolean entrada = !linha.tipo().equals("SAQUE") && !linha.tipo().equals("VENDA");
                setText(item + (entrada ? " ▲" : " ▼"));
                setStyle("-fx-text-fill: " + (entrada ? "#2e7d32" : "#c62828") + "; -fx-alignment: CENTER-RIGHT;");
            }
        });

        TableColumn<LinhaTransacao, String> cNotas = new TableColumn<>("Notas");
        cNotas.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().notas() != null ? c.getValue().notas() : ""));
        cNotas.setPrefWidth(200);

        table.getColumns().addAll(cPer, cAtivo, cTipo, cValor, cNotas);
        return table;
    }

    private String labelTipo(String tipo) {
        return switch (tipo) {
            case "DEPOSITO" -> "Depósito";
            case "SAQUE" -> "Saque";
            case "COMPRA" -> "Compra";
            case "VENDA" -> "Venda";
            case "DIVIDENDO" -> "Dividendo";
            default -> tipo;
        };
    }

    private void popularTabela(TableView<LinhaTransacao> table) {
        List<LinhaTransacao> linhas = new ArrayList<>();

        for (Investimento inv : invRepo.findAll()) {
            String nome = inv.getNome();
            for (Movimentacao mov : movRepo.findByInvestimento(inv.getId())) {
                linhas.add(new LinhaTransacao(
                        mov.getPeriodoMes(), mov.getPeriodoAno(),
                        nome, mov.getTipoMov().name(),
                        mov.getValor(), mov.getNotas()));
            }
            for (AporteRv a : aporteRepo.findByInvestimento(inv.getId())) {
                linhas.add(new LinhaTransacao(
                        a.getPeriodoMes(), a.getPeriodoAno(),
                        nome, a.getTipoOp().name(),
                        a.getValor(), a.getNotas()));
            }
        }

        linhas.sort(Comparator
                .comparingInt(LinhaTransacao::periodoAno)
                .thenComparingInt(LinhaTransacao::periodoMes)
                .reversed());

        table.getItems().setAll(linhas);
    }
}
