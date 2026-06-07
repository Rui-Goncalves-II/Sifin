package br.investimentos;

import br.investimentos.db.DatabaseManager;
import br.investimentos.repository.*;
import br.investimentos.service.*;
import br.investimentos.service.BrapiService;
import br.investimentos.service.ConfigService;
import br.investimentos.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        DatabaseManager db = DatabaseManager.getInstance();

        var invRepo   = new InvestimentoRepository();
        var movRepo   = new MovimentacaoRepository();
        var aporteRepo= new AporteRvRepository();
        var vtaRepo   = new VtaMensalRepository();
        var vacRepo   = new VacMensalRepository();
        var vaiRepo   = new VaiAnualRepository();
        var cotaRepo  = new CotacaoRepository();
        var gastoRepo = new GastoRepository();

        var taxaSvc      = new TaxaService();
        var rendSvc      = new RendimentoService(vaiRepo, movRepo, vtaRepo);
        var rvSvc        = new RendaVariavelService(aporteRepo, vacRepo);
        var saldoSvc     = new SaldoService(vtaRepo, taxaSvc, rendSvc);
        var projecaoSvc  = new ProjecaoService(vtaRepo, taxaSvc, rendSvc);
        var vaiSvc       = new VaiService(invRepo, vaiRepo, vtaRepo, db);
        var alertaSvc    = new AlertaService(invRepo, vtaRepo, vacRepo);
        var consolSvc    = new ConsolidacaoService(invRepo, movRepo, vtaRepo, vaiRepo, aporteRepo, vacRepo, rendSvc, rvSvc);
        var cotacaoSvc   = new CotacaoService(cotaRepo);
        var gastosSvc    = new GastosService(gastoRepo);
        var configSvc    = new ConfigService();
        var brapiSvc     = new BrapiService(configSvc);

        vaiSvc.virarAno();

        MainWindow window = new MainWindow(
                primaryStage, invRepo, movRepo, aporteRepo, vtaRepo, vacRepo, vaiRepo,
                gastoRepo,
                taxaSvc, rendSvc, rvSvc, saldoSvc, projecaoSvc, vaiSvc, alertaSvc, consolSvc, cotacaoSvc,
                gastosSvc, configSvc, brapiSvc
        );
        window.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
