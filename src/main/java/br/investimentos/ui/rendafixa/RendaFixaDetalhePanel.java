package br.investimentos.ui.rendafixa;

import br.investimentos.model.Investimento;
import br.investimentos.model.VtaMensal;
import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.ui.projecao.ProjecaoPanel;
import br.investimentos.ui.util.FormatUtil;
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
            new VtaFormDialog(inv, vtaRepo, vaiRepo, movRepo, rendSvc).showAndWait();
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
        double r = rendSvc.calcularR(inv.getId(), mes, ano);

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
        vtaTable.setPrefHeight(300);

        TableColumn<VtaMensal, String> cPer = new TableColumn<>("Período");
        cPer.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                FormatUtil.mesAno(c.getValue().getPeriodoMes(), c.getValue().getPeriodoAno())));
        cPer.setPrefWidth(100);

        TableColumn<VtaMensal, String> cVta = new TableColumn<>("VTA");
        cVta.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(FormatUtil.brl(c.getValue().getVta())));
        cVta.setPrefWidth(130);

        TableColumn<VtaMensal, String> cVi = new TableColumn<>("VI");
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

        vtaTable.getColumns().addAll(cPer, cVta, cVi, cR, cSomaPrev, cRMensal, cTaxa);

        vtaTable.getItems().addAll(vtas);

        content.getChildren().addAll(new VBox(4, info), sumGrid, histTitle, vtaTable);
        return content;
    }

    private VBox metricCard(String title, String value, String valueStyle) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        Label t = new Label(title); t.getStyleClass().add("card-title");
        Label v = new Label(value); v.getStyleClass().addAll("card-value-sm", valueStyle);
        card.getChildren().addAll(t, v);
        return card;
    }
}
