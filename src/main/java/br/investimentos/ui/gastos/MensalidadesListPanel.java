package br.investimentos.ui.gastos;

import br.investimentos.model.Gasto;
import br.investimentos.repository.GastoRepository;
import br.investimentos.ui.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class MensalidadesListPanel extends BorderPane {

    private final GastoRepository repo;
    private final Runnable onAtualizado;

    private TableView<Gasto> table;

    public MensalidadesListPanel(GastoRepository repo, Runnable onAtualizado) {
        this.repo = repo;
        this.onAtualizado = onAtualizado;
        construir();
    }

    private void construir() {
        Button btnNovo = new Button("+ Nova Mensalidade");
        btnNovo.getStyleClass().add("btn-primary");
        btnNovo.setOnAction(e -> abrirForm(null));

        HBox toolbar = new HBox(btnNovo);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setPadding(new Insets(8, 0, 8, 0));

        table = buildTable();

        VBox content = new VBox(8, toolbar, table);
        content.setPadding(new Insets(16, 24, 24, 24));
        VBox.setVgrow(table, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
        carregar();
    }

    private TableView<Gasto> buildTable() {
        TableView<Gasto> t = new TableView<>();
        t.getStyleClass().add("table-view");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label("Nenhuma mensalidade cadastrada."));

        TableColumn<Gasto, String> cDesc = new TableColumn<>("Descrição");
        cDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescricao()));
        cDesc.setPrefWidth(200);

        TableColumn<Gasto, String> cInicio = new TableColumn<>("Início");
        cInicio.setCellValueFactory(c -> {
            Gasto g = c.getValue();
            if (g.getPeriodoMes() == null) return new SimpleStringProperty("—");
            return new SimpleStringProperty(FormatUtil.mesAno(g.getPeriodoMes(), g.getPeriodoAno()));
        });
        cInicio.setPrefWidth(80);
        cInicio.setStyle("-fx-alignment: CENTER;");

        TableColumn<Gasto, String> cValor = new TableColumn<>("Valor/mês");
        cValor.setCellValueFactory(c -> new SimpleStringProperty(FormatUtil.brl(c.getValue().getValor())));
        cValor.setPrefWidth(110);
        cValor.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<Gasto, String> cValorAnual = new TableColumn<>("Valor/ano");
        cValorAnual.setCellValueFactory(c -> new SimpleStringProperty(FormatUtil.brl(c.getValue().getValor() * 12)));
        cValorAnual.setPrefWidth(110);
        cValorAnual.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<Gasto, String> cStatus = new TableColumn<>("Status");
        cStatus.setCellValueFactory(c -> {
            Gasto g = c.getValue();
            if (g.isAtiva()) return new SimpleStringProperty("Ativa");
            return new SimpleStringProperty("Encerrada " + FormatUtil.mesAno(g.getFimMes(), g.getFimAno()));
        });
        cStatus.setPrefWidth(120);
        cStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                boolean ativa = item.equals("Ativa");
                setStyle(ativa
                        ? "-fx-text-fill: #3fb950; -fx-font-weight: bold;"
                        : "-fx-text-fill: #8b949e;");
            }
        });

        TableColumn<Gasto, String> cNotas = new TableColumn<>("Notas");
        cNotas.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNotas() != null ? c.getValue().getNotas() : ""));

        TableColumn<Gasto, Void> cAcoes = new TableColumn<>("Ações");
        cAcoes.setMinWidth(222);
        cAcoes.setPrefWidth(222);
        cAcoes.setMaxWidth(222);
        cAcoes.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit     = new Button("Editar");
            private final Button btnEncerrar = new Button("Encerrar");
            private final Button btnRemover  = new Button("Remover");
            private final HBox box = new HBox(6, btnEdit, btnEncerrar, btnRemover);
            {
                btnEdit.getStyleClass().add("btn-secondary");
                btnEncerrar.getStyleClass().add("btn-warning");
                btnRemover.getStyleClass().add("btn-danger");
                btnEdit.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                btnEncerrar.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                btnRemover.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                box.setAlignment(Pos.CENTER);
                btnEdit.setOnAction(e -> abrirForm(getTableView().getItems().get(getIndex())));
                btnEncerrar.setOnAction(e -> encerrar(getTableView().getItems().get(getIndex())));
                btnRemover.setOnAction(e -> remover(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Gasto g = getTableView().getItems().get(getIndex());
                btnEncerrar.setDisable(!g.isAtiva());
                setGraphic(box);
            }
        });

        t.getColumns().addAll(cDesc, cInicio, cValor, cValorAnual, cStatus, cNotas, cAcoes);
        return t;
    }

    private void abrirForm(Gasto existente) {
        MensalidadeFormDialog dlg = new MensalidadeFormDialog(getScene().getWindow(), repo, existente);
        dlg.setOnSalvo(() -> { carregar(); if (onAtualizado != null) onAtualizado.run(); });
        dlg.showAndWait();
    }

    private void encerrar(Gasto g) {
        LocalDate hoje = LocalDate.now();
        String mesAno = FormatUtil.mesAno(hoje.getMonthValue(), hoje.getYear());
        Optional<ButtonType> res = new Alert(Alert.AlertType.CONFIRMATION,
                "Encerrar \"" + g.getDescricao() + "\" a partir de " + mesAno + "?\n" +
                "Os meses anteriores continuarão contabilizados.",
                ButtonType.YES, ButtonType.NO).showAndWait();
        if (res.isPresent() && res.get() == ButtonType.YES) {
            repo.encerrar(g.getId(), hoje.getMonthValue(), hoje.getYear());
            carregar();
            if (onAtualizado != null) onAtualizado.run();
        }
    }

    private void remover(Gasto g) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Remover mensalidade");
        alert.setHeaderText("Remover \"" + g.getDescricao() + "\" permanentemente?");
        alert.setContentText("Esta operação não pode ser desfeita e removerá todo o histórico desta mensalidade.");
        alert.getButtonTypes().setAll(
                new ButtonType("Remover", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL
        );
        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
            repo.deletar(g.getId());
            carregar();
            if (onAtualizado != null) onAtualizado.run();
        }
    }

    public void carregar() {
        List<Gasto> lista = repo.findMensalidades();
        table.getItems().setAll(lista);
    }
}
