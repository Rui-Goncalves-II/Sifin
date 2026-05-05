package br.investimentos.ui.gastos;

import br.investimentos.model.Gasto;
import br.investimentos.model.enums.TipoGasto;
import br.investimentos.repository.GastoRepository;
import br.investimentos.ui.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Optional;

public class GastosListPanel extends BorderPane {

    private final GastoRepository repo;
    private final TipoGasto tipo;
    private final Runnable onAtualizado;

    private TableView<Gasto> table;

    public GastosListPanel(GastoRepository repo, TipoGasto tipo, Runnable onAtualizado) {
        this.repo = repo;
        this.tipo = tipo;
        this.onAtualizado = onAtualizado;
        construir();
    }

    private void construir() {
        Button btnNovo = new Button("+ Novo");
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
        t.setPlaceholder(new Label("Nenhum registro de " + tipo.getLabel().toLowerCase() + " cadastrado."));

        TableColumn<Gasto, String> cDesc = new TableColumn<>("Descrição");
        cDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescricao()));
        cDesc.setPrefWidth(220);

        TableColumn<Gasto, String> cPer = new TableColumn<>("Período");
        cPer.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().getPeriodoMes(), c.getValue().getPeriodoAno())));
        cPer.setPrefWidth(90);

        TableColumn<Gasto, String> cValor = new TableColumn<>("Valor");
        cValor.setCellValueFactory(c -> new SimpleStringProperty(FormatUtil.brl(c.getValue().getValor())));
        cValor.setPrefWidth(120);
        cValor.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<Gasto, String> cNotas = new TableColumn<>("Notas");
        cNotas.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNotas() != null ? c.getValue().getNotas() : ""));

        TableColumn<Gasto, Void> cAcoes = new TableColumn<>("Ações");
        cAcoes.setPrefWidth(130);
        cAcoes.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("Editar");
            private final Button btnDel  = new Button("Excluir");
            private final HBox box = new HBox(6, btnEdit, btnDel);
            {
                btnEdit.getStyleClass().add("btn-secondary");
                btnDel.getStyleClass().add("btn-danger");
                box.setAlignment(Pos.CENTER);
                btnEdit.setOnAction(e -> abrirForm(getTableView().getItems().get(getIndex())));
                btnDel.setOnAction(e -> excluir(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        t.getColumns().addAll(cDesc, cPer, cValor, cNotas, cAcoes);
        return t;
    }

    private void abrirForm(Gasto existente) {
        GastoFormDialog dlg = new GastoFormDialog(getScene().getWindow(), repo, tipo, existente);
        dlg.setOnSalvo(() -> { carregar(); if (onAtualizado != null) onAtualizado.run(); });
        dlg.showAndWait();
    }

    private void excluir(Gasto g) {
        Optional<ButtonType> res = new Alert(Alert.AlertType.CONFIRMATION,
                "Excluir o gasto \"" + g.getDescricao() + "\" de " + FormatUtil.brl(g.getValor()) + "?",
                ButtonType.YES, ButtonType.NO).showAndWait();
        if (res.isPresent() && res.get() == ButtonType.YES) {
            repo.deletar(g.getId());
            carregar();
            if (onAtualizado != null) onAtualizado.run();
        }
    }

    public void carregar() {
        List<Gasto> lista = repo.findByTipo(tipo);
        table.getItems().setAll(lista);
    }
}
