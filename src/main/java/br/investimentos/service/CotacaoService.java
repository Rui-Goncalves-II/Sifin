package br.investimentos.service;

import br.investimentos.model.CotacaoDolar;
import br.investimentos.repository.CotacaoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CotacaoService {

    private static final String DOLAR_URL = "https://economia.awesomeapi.com.br/json/last/USD-BRL";

    private final CotacaoRepository cotacaoRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cotacao-refresh");
        t.setDaemon(true);
        return t;
    });

    private volatile CotacaoDolar cotacaoAtual;
    private Consumer<CotacaoDolar> onAtualizada;

    public CotacaoService(CotacaoRepository cotacaoRepo) {
        this.cotacaoRepo = cotacaoRepo;
        cotacaoAtual = cotacaoRepo.findUltima().orElse(null);
    }

    public void iniciarRefreshAutomatico(Consumer<CotacaoDolar> callback) {
        this.onAtualizada = callback;
        scheduler.scheduleAtFixedRate(this::atualizarDolar, 0, 15, TimeUnit.MINUTES);
    }

    public Optional<CotacaoDolar> getCotacaoAtual() {
        return Optional.ofNullable(cotacaoAtual);
    }

    private void atualizarDolar() {
        String hoje = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        Optional<CotacaoDolar> cacheOpt = cotacaoRepo.findByData(hoje);
        if (cacheOpt.isPresent() && cotacaoAtual != null &&
                hoje.equals(cotacaoAtual.getData()) && !deveRefrescar()) {
            return;
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(DOLAR_URL))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode usd = root.get("USDBRL");
                if (usd != null) {
                    CotacaoDolar c = new CotacaoDolar();
                    c.setData(hoje);
                    c.setValorCompra(usd.get("bid").asDouble());
                    c.setValorVenda(usd.get("ask").asDouble());
                    c.setFonte("AwesomeAPI");
                    cotacaoRepo.salvar(c);
                    cotacaoAtual = c;
                    if (onAtualizada != null) onAtualizada.accept(c);
                }
            }
        } catch (Exception e) {
            System.err.println("Falha ao buscar cotação do dólar: " + e.getMessage());
        }
    }

    private boolean deveRefrescar() {
        return false;
    }

    public void encerrar() {
        scheduler.shutdownNow();
    }
}
