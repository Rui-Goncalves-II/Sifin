package br.investimentos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class BrapiService {

    private static final String BASE_URL = "https://brapi.dev/api/quote/";
    private static final String TEST_TICKER = "PETR4";

    private final ConfigService configSvc;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public BrapiService(ConfigService configSvc) {
        this.configSvc = configSvc;
    }

    public Optional<Double> buscarPreco(String ticker) {
        String token = configSvc.getBrapiToken();
        return fetchPreco(ticker.toUpperCase().strip(), token);
    }

    public boolean testarConexao(String token) {
        return fetchPreco(TEST_TICKER, token).isPresent();
    }

    private Optional<Double> fetchPreco(String ticker, String token) {
        try {
            String url = BASE_URL + ticker;
            if (token != null && !token.isBlank()) {
                url += "?token=" + token;
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode results = root.get("results");
                if (results != null && results.isArray() && !results.isEmpty()) {
                    JsonNode price = results.get(0).get("regularMarketPrice");
                    if (price != null && !price.isNull()) {
                        return Optional.of(price.asDouble());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Falha ao buscar cotação BRAPI (" + ticker + "): " + e.getMessage());
        }
        return Optional.empty();
    }
}
