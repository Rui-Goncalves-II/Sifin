package br.investimentos.ui.rendafixa;

import br.investimentos.model.Investimento;
import br.investimentos.model.Movimentacao;
import br.investimentos.model.VtaMensal;
import br.investimentos.model.enums.TipoMovimentacao;
import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.ui.projecao.ProjecaoPanel;
import br.investimentos.ui.util.FormatUtil;
import br.investimentos.ui.util.GlossarioTooltip;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RendaFixaDetalhePanel extends BorderPane {

    private final Investimento inv;
    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final VtaMensalRepository vtaRepo;
    private final VaiAnualRepository vaiRepo;
    private final RendimentoService rendSvc;
    private final TaxaService taxaSvc;
    private final SaldoService saldoSvc;
    private final Consumer<Node> navigate;

    public RendaFixaDetalhePanel(Investimento inv, InvestimentoRepository invRepo,
                                  MovimentacaoRepository movRepo, VtaMensalRepository vtaRepo,
                                  VaiAnualRepository vaiRepo, RendimentoService rendSvc,
                                  TaxaService taxaSvc, SaldoService saldoSvc,
                                  Consumer<Node> navigate) {
        this.inv = inv; this.invRepo = invRepo; this.movRepo = movRepo;
        this.vtaRepo = vtaRepo; this.vaiRepo = vaiRepo; this.rendSvc = rendSvc;
        this.taxaSvc = taxaSvc; this.saldoSvc = saldoSvc; this.navigate = navigate;
        construir();
    }

    private void construir() {
        // Header
        HBox header = new HBox(12);
        header.getStyleClass().add("dash-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Button btnVoltar = new Button("← Voltar");
        btnVoltar.getStyleClass().add("btn-secondary");
        btnVoltar.setOnAction(e -> navigate.accept(new RendaFixaListPanel(
                invRepo, movRepo, vtaRepo, vaiRepo, rendSvc, taxaSvc, saldoSvc, navigate)));
        Label title = new Label("🏦 " + inv.getNome());
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnVta = new Button("+ VTA");
        btnVta.getStyleClass().add("btn-primary");
        btnVta.setOnAction(e -> {
            new VtaFormDialog(inv, vtaRepo, vaiRepo, rendSvc).showAndWait();
            construir();
        });
        Button btnProjecao = new Button("📈 Projeção");
        btnProjecao.getStyleClass().add("btn-secondary");
        btnProjecao.setOnAction(e -> {
            ProjecaoService projecaoSvc = new ProjecaoService(vtaRepo, taxaSvc, rendSvc);
            navigate.accept(new ProjecaoPanel(invRepo, projecaoSvc, navigate));
        });
        header.getChildren().addAll(btnVoltar, title, spacer, btnProjecao, btnVta);
        setTop(header);

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        setCenter(scroll);
    }

    private VBox buildContent() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 24, 24));

        // Sumário
        LocalDate hoje = LocalDate.now();
        int mes = hoje.getMonthValue();
        int ano = hoje.getYear();
        double saldoAtual = saldoSvc.saldoAtual(inv, 0);
        double vai = vaiRepo.getVai(inv.getId(), ano);
        double sam = rendSvc.calcularSam(inv.getId(), ano, mes);
        double vi = vai + sam;
        // R acumulado = R do último VTA registrado no ano (já inclui todos os meses anteriores)
        double r = vtaRepo.findUltimoDoAno(inv.getId(), ano)
                .map(rendSvc::calcularR)
                .orElse(0.0);

        GridPane sumGrid = new GridPane();
        sumGrid.setHgap(12); sumGrid.setVgap(12);
        ColumnConstraints cc = new ColumnConstraints(); cc.setPercentWidth(25); cc.setHgrow(Priority.ALWAYS);
        sumGrid.getColumnConstraints().addAll(cc, cc, cc, cc);

        sumGrid.add(metricCard("Saldo Atual", FormatUtil.brl(saldoAtual), "neutral"), 0, 0);
        sumGrid.add(metricCard("VAI " + ano, FormatUtil.brl(vai), "neutral"), 1, 0);
        sumGrid.add(metricCard("SAM " + FormatUtil.mesAno(mes, ano), FormatUtil.brl(sam), "neutral"), 2, 0);
        sumGrid.add(metricCard("Rendimento (R)", FormatUtil.brl(r), r >= 0 ? "positive" : "negative"), 3, 0);

        // Info do ativo
        String subInfo = (inv.getSubtipo() != null ? inv.getSubtipo() : "—") +
                " / " + (inv.getIndexador() != null ? inv.getIndexador() : "—");
        String taxaInfo = inv.getTaxaAnual() != null ? FormatUtil.pct(inv.getTaxaAnual()) + " a.a." : "calculada";
        Label info = new Label("Subtipo: " + subInfo + "  |  Taxa: " + taxaInfo +
                (inv.getDataVencimento() != null ? "  |  Vencimento: " + inv.getDataVencimento() : ""));
        info.getStyleClass().add("card-label");

        // Histórico VTA
        Label histTitle = new Label("Histórico de VTA");
        histTitle.getStyleClass().add("section-title");

        TableView<VtaMensal> vtaTable = new TableView<>();
        vtaTable.getStyleClass().add("table-view");
        vtaTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        vtaTable.setPrefHeight(300);

        TableColumn<VtaMensal, String> cPer = new TableColumn<>("Período");
        cPer.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().getPeriodoMes(), c.getValue().getPeriodoAno())));
        cPer.setPrefWidth(100);

        TableColumn<VtaMensal, String> cVta = new TableColumn<>();
        cVta.setGraphic(colHeader("VTA"));
        cVta.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(c.getValue().getVta())));
        cVta.setPrefWidth(130);

        TableColumn<VtaMensal, String> cVi = new TableColumn<>();
        cVi.setGraphic(colHeader("VI"));
        cVi.setCellValueFactory(c -> {
            VtaMensal v = c.getValue();
            double viVal = rendSvc.calcularVi(v.getInvestimentoId(), v.getPeriodoAno(), v.getPeriodoMes());
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(viVal));
        });
        cVi.setPrefWidth(130);

        List<VtaMensal> vtas = vtaRepo.findByInvestimento(inv.getId());

        // Pre-computar soma dos rendimentos anteriores para cada VTA (evita N queries)
        Map<Integer, Double> somaAntByVtaId = new HashMap<>();
        double prevR = 0;
        int prevAno = -1;
        for (VtaMensal v : vtas) {
            if (v.getPeriodoAno() != prevAno) {
                prevAno = v.getPeriodoAno();
                prevR = 0;
            }
            somaAntByVtaId.put(v.getId(), prevR);
            prevR = rendSvc.calcularR(v);
        }

        TableColumn<VtaMensal, String> cR = new TableColumn<>("Rend. Acumulado");
        cR.setCellValueFactory(c -> {
            double rVal = rendSvc.calcularR(c.getValue());
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(rVal));
        });
        cR.setPrefWidth(130);

        TableColumn<VtaMensal, String> cSomaPrev = new TableColumn<>("Rend. Ant.");
        cSomaPrev.setCellValueFactory(c -> {
            double soma = somaAntByVtaId.getOrDefault(c.getValue().getId(), 0.0);
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(soma));
        });
        cSomaPrev.setPrefWidth(120);

        TableColumn<VtaMensal, String> cRMensal = new TableColumn<>("Rend. Mensal");
        cRMensal.setCellValueFactory(c -> {
            VtaMensal v = c.getValue();
            double rMensal = rendSvc.calcularR(v) - somaAntByVtaId.getOrDefault(v.getId(), 0.0);
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(rMensal));
        });
        cRMensal.setPrefWidth(120);

        TableColumn<VtaMensal, String> cTaxa = new TableColumn<>("Taxa Acum.");
        cTaxa.setCellValueFactory(c -> {
            VtaMensal v = c.getValue();
            double viVal = rendSvc.calcularVi(v.getInvestimentoId(), v.getPeriodoAno(), v.getPeriodoMes());
            double rVal = rendSvc.calcularR(v);
            double ta = taxaSvc.taxaAcumulada(rVal, viVal);
            return new javafx.beans.property.SimpleStringProperty(FormatUtil.pct(ta * 100));
        });
        cTaxa.setPrefWidth(100);

        TableColumn<VtaMensal, Void> cAcoes = new TableColumn<>("Ações");
        cAcoes.setPrefWidth(150);
        cAcoes.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("✏ Editar");
            private final Button btnDel  = new Button("🗑 Excluir");
            {
                btnEdit.getStyleClass().add("btn-secondary");
                btnDel.getStyleClass().add("btn-danger");
                btnEdit.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");
                btnDel.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");

                btnEdit.setOnAction(e -> {
                    VtaMensal v = getTableView().getItems().get(getIndex());
                    new VtaFormDialog(inv, vtaRepo, vaiRepo, rendSvc, v).showAndWait();
                    construir();
                });

                btnDel.setOnAction(e -> {
                    VtaMensal v = getTableView().getItems().get(getIndex());
                    Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                            "Excluir VTA de " + FormatUtil.mesAno(v.getPeriodoMes(), v.getPeriodoAno()) + "?",
                            ButtonType.YES, ButtonType.NO);
                    conf.setTitle("Confirmar exclusão");
                    conf.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) {
                            vtaRepo.deletar(v.getId());
                            construir();
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : new HBox(6, btnEdit, btnDel));
            }
        });

        vtaTable.getColumns().addAll(cPer, cVta, cVi, cR, cSomaPrev, cRMensal, cTaxa, cAcoes);
        vtaTable.getItems().addAll(vtas);

        // ── Movimentações ────────────────────────────────────────────────
        Label movTitle = new Label("Movimentações (Depósitos e Saques)");
        movTitle.getStyleClass().add("section-title");

        Button btnNovaMov = new Button("+ Movimentação");
        btnNovaMov.getStyleClass().add("btn-primary");
        btnNovaMov.setStyle("-fx-font-size: 16px; -fx-padding: 5 14;");
        btnNovaMov.setOnAction(e -> {
            Movimentacao nova = new Movimentacao();
            new MovimentacaoFormDialog(inv, nova, movRepo).showAndWait();
            construir();
        });
        HBox movHeader = new HBox(12, movTitle, btnNovaMov);
        movHeader.setAlignment(Pos.CENTER_LEFT);

        TableView<Movimentacao> movTable = new TableView<>();
        movTable.getStyleClass().add("table-view");
        movTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        movTable.setPrefHeight(200);

        TableColumn<Movimentacao, String> mPer = new TableColumn<>("Período");
        mPer.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().getPeriodoMes(), c.getValue().getPeriodoAno())));
        mPer.setPrefWidth(100);

        TableColumn<Movimentacao, String> mTipo = new TableColumn<>("Tipo");
        mTipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getTipoMov() == TipoMovimentacao.DEPOSITO ? "Depósito" : "Saque"));
        mTipo.setPrefWidth(100);

        TableColumn<Movimentacao, String> mValor = new TableColumn<>("Valor");
        mValor.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.brl(c.getValue().getValor())));
        mValor.setPrefWidth(130);
        mValor.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getTableRow().getItem() == null) {
                    setText(null); setStyle(""); return;
                }
                boolean entrada = getTableRow().getItem().getTipoMov() == TipoMovimentacao.DEPOSITO;
                setText(item + (entrada ? " ▲" : " ▼"));
                setStyle("-fx-text-fill: " + (entrada ? "#2e7d32" : "#c62828") + ";");
            }
        });

        TableColumn<Movimentacao, String> mNotas = new TableColumn<>("Notas");
        mNotas.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getNotas() != null ? c.getValue().getNotas() : ""));
        mNotas.setPrefWidth(200);

        TableColumn<Movimentacao, Void> mAcoes = new TableColumn<>("Ações");
        mAcoes.setPrefWidth(150);
        mAcoes.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("✏ Editar");
            private final Button btnDel  = new Button("🗑 Excluir");
            {
                btnEdit.getStyleClass().add("btn-secondary");
                btnDel.getStyleClass().add("btn-danger");
                btnEdit.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");
                btnDel.setStyle("-fx-font-size: 15px; -fx-padding: 3 8;");

                btnEdit.setOnAction(e -> {
                    Movimentacao m = getTableView().getItems().get(getIndex());
                    new MovimentacaoFormDialog(inv, m, movRepo).showAndWait();
                    construir();
                });

                btnDel.setOnAction(e -> {
                    Movimentacao m = getTableView().getItems().get(getIndex());
                    Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                            "Excluir movimentação de " + FormatUtil.brl(m.getValor()) + "?",
                            ButtonType.YES, ButtonType.NO);
                    conf.setTitle("Confirmar exclusão");
                    conf.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) {
                            movRepo.deletar(m.getId());
                            construir();
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : new HBox(6, btnEdit, btnDel));
            }
        });

        movTable.getColumns().addAll(mPer, mTipo, mValor, mNotas, mAcoes);
        movTable.getItems().addAll(movRepo.findByInvestimento(inv.getId()));

        content.getChildren().addAll(new VBox(4, info), sumGrid, histTitle, vtaTable,
                movHeader, movTable);
        return content;
    }

    private Label colHeader(String sigla) {
        Label lbl = new Label(sigla);
        lbl.setStyle("-fx-text-fill: #7d8fa0; -fx-font-size: 15px; -fx-font-weight: bold;");
        GlossarioTooltip.aplicar(lbl, sigla);
        return lbl;
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
