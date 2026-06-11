package br.investimentos.ui;

import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.service.BrapiService;
import br.investimentos.service.ConfigService;
import br.investimentos.ui.component.SideBar;
import br.investimentos.ui.dashboard.DashboardPanel;
import br.investimentos.ui.dolar.DolarListPanel;
import br.investimentos.ui.gastos.GastosPanel;
import br.investimentos.ui.rendafixa.RendaFixaListPanel;
import br.investimentos.ui.rendavariavel.RendaVariavelListPanel;
import br.investimentos.service.ImportExportService;
import br.investimentos.ui.configuracoes.ConfiguracoesPanel;
import br.investimentos.ui.transacao.TransacaoPanel;
import br.investimentos.ui.util.FormatUtil;
import br.investimentos.service.AtualizacaoService;
import br.investimentos.service.VacAutoService;
import br.investimentos.ui.util.AtualizacaoDialog;
import br.investimentos.ui.util.Toast;
import java.io.IOException;
import java.io.InputStream;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import br.investimentos.ui.util.UIUtil;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;


public class MainWindow {

    private final Stage stage;
    private final StackPane contentArea;
    private final SideBar sidebar;
    private StackPane rootLayer;

    private final InvestimentoRepository invRepo;
    private final MovimentacaoRepository movRepo;
    private final AporteRvRepository aporteRepo;
    private final VtaMensalRepository vtaRepo;
    private final VacMensalRepository vacRepo;
    private final VaiAnualRepository vaiRepo;
    private final GastoRepository gastoRepo;
    private final TaxaService taxaSvc;
    private final RendimentoService rendSvc;
    private final RendaVariavelService rvSvc;
    private final SaldoService saldoSvc;
    private final ProjecaoService projecaoSvc;
    private final VaiService vaiSvc;
    private final AlertaService alertaSvc;
    private final ConsolidacaoService consolSvc;
    private final CotacaoService cotacaoSvc;
    private final GastosService gastosSvc;
    private final ConfigService configSvc;
    private final BrapiService brapiSvc;

    public MainWindow(Stage stage,
                      InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
                      AporteRvRepository aporteRepo, VtaMensalRepository vtaRepo,
                      VacMensalRepository vacRepo, VaiAnualRepository vaiRepo,
                      GastoRepository gastoRepo,
                      TaxaService taxaSvc, RendimentoService rendSvc,
                      RendaVariavelService rvSvc, SaldoService saldoSvc,
                      ProjecaoService projecaoSvc, VaiService vaiSvc, AlertaService alertaSvc,
                      ConsolidacaoService consolSvc, CotacaoService cotacaoSvc,
                      GastosService gastosSvc, ConfigService configSvc, BrapiService brapiSvc) {
        this.stage = stage;
        this.invRepo = invRepo; this.movRepo = movRepo; this.aporteRepo = aporteRepo;
        this.vtaRepo = vtaRepo; this.vacRepo = vacRepo; this.vaiRepo = vaiRepo;
        this.gastoRepo = gastoRepo;
        this.taxaSvc = taxaSvc; this.rendSvc = rendSvc; this.rvSvc = rvSvc;
        this.saldoSvc = saldoSvc; this.projecaoSvc = projecaoSvc; this.vaiSvc = vaiSvc;
        this.alertaSvc = alertaSvc; this.consolSvc = consolSvc; this.cotacaoSvc = cotacaoSvc;
        this.gastosSvc = gastosSvc; this.configSvc = configSvc; this.brapiSvc = brapiSvc;

        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");

        sidebar = new SideBar();

        sidebar.addItem(new SideBar.NavItem("🏠", "Dashboard", () -> loadPanel(makeDashboard())));
        sidebar.addItem(new SideBar.NavItem(null, loadSidebarIcon("/icons/renda-passiva.png", 22), "Renda Fixa", () -> loadPanel(makeRendaFixa()), false));
        sidebar.addItem(new SideBar.NavItem("💹", "Renda Variável", () -> loadPanel(makeRendaVariavel())));
        sidebar.addItem(new SideBar.NavItem("$", "Dólar", () -> loadPanel(makeDolar())));
        sidebar.addItem(new SideBar.NavItem(null, loadSidebarIcon("/icons/dinheiro.png", 22), "Gastos", () -> loadPanel(makeGastos()), false));
        sidebar.addItem(new SideBar.NavItem("⇄", "Transações", () -> loadPanel(makeTransacoes())));
        sidebar.addSpacer();
        sidebar.addItem(new SideBar.NavItem(null, loadSidebarIcon("/icons/configuracoes.png", 22), "Configurações", () -> loadPanel(makeConfiguracoes()), false));

        loadPanel(makeDashboard());
        sidebar.activateFirst();
    }

    private Node makeDashboard() {
        return new DashboardPanel(invRepo, aporteRepo, vtaRepo, vacRepo,
                rendSvc, rvSvc, saldoSvc, consolSvc, cotacaoSvc, alertaSvc, gastosSvc, this::loadPanel);
    }

    private Node makeRendaFixa() {
        return new RendaFixaListPanel(invRepo, movRepo, vtaRepo, vaiRepo,
                rendSvc, taxaSvc, saldoSvc, vaiSvc, this::loadPanel);
    }

    private Node makeRendaVariavel() {
        return new RendaVariavelListPanel(invRepo, aporteRepo, vacRepo, rvSvc, cotacaoSvc, brapiSvc, this::loadPanel);
    }

    private Node makeDolar() {
        return new DolarListPanel(invRepo, movRepo, consolSvc, cotacaoSvc, this::loadPanel);
    }

    private Node makeTransacoes() {
        ImportExportService impExpSvc = new ImportExportService(invRepo, movRepo, vtaRepo, vaiRepo, aporteRepo, vacRepo, gastoRepo);
        return new TransacaoPanel(invRepo, movRepo, aporteRepo, impExpSvc);
    }

    private Node makeGastos() {
        return new GastosPanel(gastoRepo, gastosSvc);
    }

    private Node makeConfiguracoes() {
        return new ConfiguracoesPanel(this::loadPanel, this::makeDashboard, rootLayer, configSvc, brapiSvc);
    }

    private ImageView loadSidebarIcon(String resourcePath, int size) {
        return UIUtil.loadTintedIcon(getClass(), resourcePath, size);
    }

    public void loadPanel(Node panel) {
        contentArea.getChildren().setAll(panel);
    }

    private void carregarIcones(Stage stage) {
        for (String res : new String[]{"/icons/logo-s-16.png", "/icons/logo-s-32.png",
                                       "/icons/logo-s-48.png", "/icons/logo-s-256.png"}) {
            try (InputStream s = getClass().getResourceAsStream(res)) {
                if (s != null) stage.getIcons().add(new Image(s));
            } catch (IOException ignored) {}
        }
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(contentArea);

        rootLayer = new StackPane(root);

        Scene scene = new Scene(rootLayer, 1280, 768);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle("Sifin — Painel de Investimentos");
        carregarIcones(stage);
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.show();

        var atualizacaoSvc = new AtualizacaoService();
        atualizacaoSvc.verificarAtualizacaoAsync(commitRemoto -> {
            String localCache = atualizacaoSvc.getCommitLocalCache();
            String localShort = localCache != null ? localCache.substring(0, 7) : "local";
            Platform.runLater(() -> AtualizacaoDialog.show(
                stage,
                localShort,
                commitRemoto,
                AtualizacaoService.getReleaseUrl()
            ));
        });

        new VacAutoService(invRepo, vacRepo, brapiSvc)
                .atualizarVacsAsync(msg -> Toast.show(rootLayer, "VAC atualizado", msg));

        cotacaoSvc.iniciarRefreshAutomatico(cot -> Platform.runLater(() -> {
            if (!contentArea.getChildren().isEmpty() &&
                    contentArea.getChildren().get(0) instanceof DashboardPanel dp) {
                dp.onCotacaoAtualizada();
            }
            String msg = "Compra: R$ " + FormatUtil.numero(cot.getValorCompra(), 4)
                       + "   Venda: R$ " + FormatUtil.numero(cot.getValorVenda(), 4);
            Toast.show(rootLayer, "Cotação USD atualizada", msg);
        }));
    }

}
