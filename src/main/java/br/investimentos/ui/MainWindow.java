package br.investimentos.ui;

import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.ui.component.SideBar;
import br.investimentos.ui.dashboard.DashboardPanel;
import br.investimentos.ui.dolar.DolarListPanel;
import br.investimentos.ui.rendafixa.RendaFixaListPanel;
import br.investimentos.ui.rendavariavel.RendaVariavelListPanel;
import br.investimentos.ui.transacao.TransacaoPanel;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;


public class MainWindow {

    private final Stage stage;
    private final StackPane contentArea;
    private final SideBar sidebar;

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final AporteRvRepository aporteRepo;
    private final VtaMensalRepository vtaRepo;
    private final VacMensalRepository vacRepo;
    private final VaiAnualRepository vaiRepo;
    private final TaxaService taxaSvc;
    private final RendimentoService rendSvc;
    private final RendaVariavelService rvSvc;
    private final SaldoService saldoSvc;
    private final ProjecaoService projecaoSvc;
    private final AlertaService alertaSvc;
    private final ConsolidacaoService consolSvc;
    private final CotacaoService cotacaoSvc;

    public MainWindow(Stage stage,
                      InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
                      AporteRvRepository aporteRepo, VtaMensalRepository vtaRepo,
                      VacMensalRepository vacRepo, VaiAnualRepository vaiRepo,
                      TaxaService taxaSvc, RendimentoService rendSvc,
                      RendaVariavelService rvSvc, SaldoService saldoSvc,
                      ProjecaoService projecaoSvc, AlertaService alertaSvc,
                      ConsolidacaoService consolSvc, CotacaoService cotacaoSvc) {
        this.stage = stage;
        this.invRepo = invRepo; this.movRepo = movRepo; this.aporteRepo = aporteRepo;
        this.vtaRepo = vtaRepo; this.vacRepo = vacRepo; this.vaiRepo = vaiRepo;
        this.taxaSvc = taxaSvc; this.rendSvc = rendSvc; this.rvSvc = rvSvc;
        this.saldoSvc = saldoSvc; this.projecaoSvc = projecaoSvc;
        this.alertaSvc = alertaSvc; this.consolSvc = consolSvc; this.cotacaoSvc = cotacaoSvc;

        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");

        sidebar = new SideBar();

        sidebar.addItem(new SideBar.NavItem("🏠", "Dashboard", () -> loadPanel(makeDashboard())));
        sidebar.addItem(new SideBar.NavItem("🏦", "Renda Fixa", () -> loadPanel(makeRendaFixa())));
        sidebar.addItem(new SideBar.NavItem("💹", "Renda Variável", () -> loadPanel(makeRendaVariavel())));
        sidebar.addItem(new SideBar.NavItem("💵", "Dólar", () -> loadPanel(makeDolar())));
        sidebar.addItem(new SideBar.NavItem("🔄", "Transações", () -> loadPanel(makeTransacoes())));
        sidebar.addSpacer();
        sidebar.addSeparator();
        sidebar.addItem(new SideBar.NavItem("💸", "Gastos", null, true));

        // Navega para o Dashboard por padrão
        loadPanel(makeDashboard());
        sidebar.activateFirst();
    }

    private Node makeDashboard() {
        return new DashboardPanel(invRepo, movRepo, aporteRepo, vtaRepo, vacRepo,
                rendSvc, rvSvc, saldoSvc, consolSvc, cotacaoSvc, alertaSvc, this::loadPanel);
    }

    private Node makeRendaFixa() {
        return new RendaFixaListPanel(invRepo, movRepo, vtaRepo, vaiRepo,
                rendSvc, taxaSvc, saldoSvc, this::loadPanel);
    }

    private Node makeRendaVariavel() {
        return new RendaVariavelListPanel(invRepo, aporteRepo, vacRepo, rvSvc, cotacaoSvc, this::loadPanel);
    }

    private Node makeDolar() {
        return new DolarListPanel(invRepo, movRepo, cotacaoSvc, this::loadPanel);
    }

    private Node makeTransacoes() {
        return new TransacaoPanel(invRepo, movRepo, aporteRepo);
    }

    public void loadPanel(Node panel) {
        contentArea.getChildren().setAll(panel);
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(contentArea);

        Scene scene = new Scene(root, 1280, 768);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle("Sifin — Painel de Investimentos");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.show();

        cotacaoSvc.iniciarRefreshAutomatico(cot -> {
            // notificação de atualização de cotação já é tratada pelo DashboardPanel via polling ou callback
        });
    }

}
