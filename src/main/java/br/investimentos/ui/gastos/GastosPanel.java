package br.investimentos.ui.gastos;

import br.investimentos.model.enums.TipoGasto;
import br.investimentos.repository.GastoRepository;
import br.investimentos.service.GastosService;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class GastosPanel extends BorderPane {

    private final GastoRepository repo;
    private final GastosService svc;

    private GastosDashboardPanel dashPanel;

    public GastosPanel(GastoRepository repo, GastosService svc) {
        this.repo = repo;
        this.svc = svc;
        construir();
    }

    private void construir() {
        dashPanel = new GastosDashboardPanel(svc);

        GastosListPanel aliPanel = new GastosListPanel(repo, TipoGasto.ALIMENTAR, this::onDadosAlterados);
        GastosListPanel divPanel = new GastosListPanel(repo, TipoGasto.DIVERSO,   this::onDadosAlterados);
        GastosListPanel menPanel = new GastosListPanel(repo, TipoGasto.MENSALIDADE, this::onDadosAlterados);

        Tab tabDash = new Tab("📊 Dashboard", dashPanel);
        tabDash.setClosable(false);

        Tab tabAli = new Tab("🍽 Alimentar", aliPanel);
        tabAli.setClosable(false);

        Tab tabDiv = new Tab("🧾 Diversos", divPanel);
        tabDiv.setClosable(false);

        Tab tabMen = new Tab("💳 Mensalidades", menPanel);
        tabMen.setClosable(false);

        TabPane tabs = new TabPane(tabDash, tabAli, tabDiv, tabMen);
        tabs.getStyleClass().add("tab-pane");
        tabs.setTabMinWidth(120);

        setCenter(tabs);
    }

    private void onDadosAlterados() {
        dashPanel.refresh();
    }
}
