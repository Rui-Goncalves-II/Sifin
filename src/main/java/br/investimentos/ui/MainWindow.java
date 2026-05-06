package br.investimentos.ui;

import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.ui.component.SideBar;
import br.investimentos.ui.dashboard.DashboardPanel;
import br.investimentos.ui.dolar.DolarListPanel;
import br.investimentos.ui.gastos.GastosPanel;
import br.investimentos.ui.rendafixa.RendaFixaListPanel;
import br.investimentos.ui.rendavariavel.RendaVariavelListPanel;
import br.investimentos.ui.transacao.TransacaoPanel;
import br.investimentos.ui.util.FormatUtil;
import br.investimentos.ui.util.Toast;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
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
    private final GastoRepository gastoRepo;
    private final TaxaService taxaSvc;
    private final RendimentoService rendSvc;
    private final RendaVariavelService rvSvc;
    private final SaldoService saldoSvc;
    private final ProjecaoService projecaoSvc;
    private final AlertaService alertaSvc;
    private final ConsolidacaoService consolSvc;
    private final CotacaoService cotacaoSvc;
    private final GastosService gastosSvc;

    public MainWindow(Stage stage,
                      InvestimentoRepository invRepo, MovimentacaoRepository movRepo,
                      AporteRvRepository aporteRepo, VtaMensalRepository vtaRepo,
                      VacMensalRepository vacRepo, VaiAnualRepository vaiRepo,
                      GastoRepository gastoRepo,
                      TaxaService taxaSvc, RendimentoService rendSvc,
                      RendaVariavelService rvSvc, SaldoService saldoSvc,
                      ProjecaoService projecaoSvc, AlertaService alertaSvc,
                      ConsolidacaoService consolSvc, CotacaoService cotacaoSvc,
                      GastosService gastosSvc) {
        this.stage = stage;
        this.invRepo = invRepo; this.movRepo = movRepo; this.aporteRepo = aporteRepo;
        this.vtaRepo = vtaRepo; this.vacRepo = vacRepo; this.vaiRepo = vaiRepo;
        this.gastoRepo = gastoRepo;
        this.taxaSvc = taxaSvc; this.rendSvc = rendSvc; this.rvSvc = rvSvc;
        this.saldoSvc = saldoSvc; this.projecaoSvc = projecaoSvc;
        this.alertaSvc = alertaSvc; this.consolSvc = consolSvc; this.cotacaoSvc = cotacaoSvc;
        this.gastosSvc = gastosSvc;

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

        loadPanel(makeDashboard());
        sidebar.activateFirst();
    }

    private Node makeDashboard() {
        return new DashboardPanel(invRepo, movRepo, aporteRepo, vtaRepo, vacRepo,
                rendSvc, rvSvc, saldoSvc, consolSvc, cotacaoSvc, alertaSvc, gastosSvc, this::loadPanel);
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

    private Node makeGastos() {
        return new GastosPanel(gastoRepo, gastosSvc);
    }

    private ImageView loadSidebarIcon(String resourcePath, int size) {
        Image original = new Image(
            getClass().getResourceAsStream(resourcePath),
            size * 2, size * 2, true, true
        );
        int w = (int) original.getWidth();
        int h = (int) original.getHeight();
        WritableImage result = new WritableImage(w, h);
        PixelReader reader = original.getPixelReader();
        PixelWriter writer = result.getPixelWriter();
        Color target = Color.web("#adbac7");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color px = reader.getColor(x, y);
                double lum = 0.299 * px.getRed() + 0.587 * px.getGreen() + 0.114 * px.getBlue();
                double alpha = (1.0 - lum) * px.getOpacity();
                if (alpha > 0.05) {
                    writer.setColor(x, y, new Color(
                        target.getRed(), target.getGreen(), target.getBlue(),
                        Math.min(alpha, 1.0)
                    ));
                } else {
                    writer.setColor(x, y, Color.TRANSPARENT);
                }
            }
        }
        ImageView iv = new ImageView(result);
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setSmooth(true);
        return iv;
    }

    public void loadPanel(Node panel) {
        contentArea.getChildren().setAll(panel);
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(contentArea);

        StackPane rootLayer = new StackPane(root);

        Scene scene = new Scene(rootLayer, 1280, 768);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle("Sifin — Painel de Investimentos");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.show();

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
