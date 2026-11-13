package br.investimentos.ui.rendavariavel;

import br.investimentos.model.Investimento;
import br.investimentos.model.enums.TipoInvestimento;
import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.service.BrapiService;
import br.investimentos.ui.util.FormatUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import br.investimentos.ui.util.UIUtil;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RendaVariavelListPanel extends BorderPane {

    private final InvestimentoRepository invRepo;
    private final AporteRvRepository aporteRepo;
    private final VacMensalRepository vacRepo;
    private final RendaVariavelService rvSvc;
    private final CotacaoService cotacaoSvc;
    private final BrapiService brapiSvc;
    private final Consumer<Node> navigate;

    private TableView<Investimento> table;
    private final HBox resumo = new HBox(12);
    private String filtroSub = null;

    public RendaVariavelListPanel(InvestimentoRepository invRepo, AporteRvRepository aporteRepo,
                                   VacMensalRepository vacRepo, RendaVariavelService rvSvc,
                                   CotacaoService cotacaoSvc, BrapiService brapiSvc, Consumer<Node> navigate) {
        this.invRepo = invRepo; this.aporteRepo = aporteRepo; this.vacRepo = vacRepo;
        this.rvSvc = rvSvc; this.cotacaoSvc = cotacaoSvc; this.brapiSvc = brapiSvc; this.navigate = navigate;
        construir();
    }

    private void construir() {
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Renda Variável", UIUtil.svgIcon(UIUtil.ICON_RENDA_VARIAVEL, 22));
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

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
        colAcoes.setMinWidth(60);
        colAcoes.setPrefWidth(60);
        colAcoes.setMaxWidth(60);
        colAcoes.setCellFactory(tc -> new TableCell<>() {
            final Button btnEdit = new Button("✏");
            final HBox box = new HBox(4, btnEdit);
            {
                btnEdit.getStyleClass().add("btn-icon");
                btnEdit.setOnAction(e -> {
                    new RendaVariavelFormDialog(getTableView().getItems().get(getIndex()), invRepo).showAndWait();
                    refresh();
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
                navigate.accept(new RendaVariavelDetalhePanel(inv, invRepo, aporteRepo, vacRepo, rvSvc, cotacaoSvc, brapiSvc, navigate));
            }
        });

        VBox content = new VBox(16, resumo, table);
        content.setPadding(new Insets(20, 24, 24, 24));
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
        refresh();
    }

    private void refresh() {
        var all = invRepo.findByTipo(TipoInvestimento.RENDA_VARIAVEL);
        if (filtroSub != null) all = all.stream().filter(i -> filtroSub.equals(i.getSubtipo())).toList();
        table.getItems().setAll(all);
        atualizarResumo(all);
    }

    /** Crescimento consolidado da RV (ativos do filtro atual), somando valores — não percentuais. */
    private void atualizarResumo(List<Investimento> ativos) {
        int ano = LocalDate.now().getYear();
        List<CrescimentoService.Crescimento> doAno = new ArrayList<>();
        List<CrescimentoService.Crescimento> total = new ArrayList<>();
        for (Investimento inv : ativos) {
            var aportes = aporteRepo.findByInvestimento(inv.getId());
            var vacs = vacRepo.findByInvestimento(inv.getId());
            doAno.add(CrescimentoService.rendaVariavel(aportes, vacs, ano));
            total.add(CrescimentoService.rendaVariavel(aportes, vacs, CrescimentoService.ANO_TODOS));
        }
        String escopo = filtroSub == null ? "Renda Variável" : filtroSub;
        resumo.getChildren().setAll(
                card("Crescimento " + escopo + " " + ano, "VAC × QC (fim de " + (ano - 1) + ")", doAno),
                card("Crescimento " + escopo + " Total", "Valor inicial", total));
    }

    private VBox card(String titulo, String rotuloAnterior, List<CrescimentoService.Crescimento> itens) {
        long semValor = itens.stream().filter(c -> c.valorAtual() == null).count();
        String nota = "Consolidado de " + itens.size() + " ativo(s): soma das bases e dos valores atuais "
                + "(% ponderado pelo tamanho de cada posição, não soma de %).\n"
                + "Dividendos não entram. Vendas descontam dos aportes."
                + (semValor > 0 ? "\n" + semValor + " ativo(s) sem VAC registrado foram excluídos." : "");
        VBox card = UIUtil.cardCrescimento(titulo, rotuloAnterior, CrescimentoService.somar(itens), nota);
        card.setMinWidth(260);
        return card;
    }
}
