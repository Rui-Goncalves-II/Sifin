package br.investimentos.service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AtualizacaoService {

    private static final String API_URL =
        "https://api.github.com/repos/Rui-Goncalves-II/Sifin/releases/latest";
    private static final String RELEASES_URL =
        "https://github.com/Rui-Goncalves-II/Sifin/releases/latest";

    public static String getReleaseUrl() {
        return RELEASES_URL;
    }

    public String getVersaoAtual() {
        try (InputStream is = getClass().getResourceAsStream("/version.properties")) {
            if (is == null) return "0.0.0";
            Properties props = new Properties();
            props.load(is);
            return props.getProperty("app.version", "0.0.0");
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    /** Verifica assincronamente. Chama onNovaVersao(tagName) se houver versão mais recente. */
    public void verificarAtualizacaoAsync(Consumer<String> onNovaVersao) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Sifin-App")
                    .build();
                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String tagName = extrairTagName(response.body());
                    if (tagName != null && isMaisRecente(tagName, getVersaoAtual())) {
                        onNovaVersao.accept(tagName);
                    }
                }
            } catch (Exception ignored) {
                // sem rede ou repo sem release — ignora silenciosamente
            }
        });
    }

    private String extrairTagName(String json) {
        int idx = json.indexOf("\"tag_name\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    boolean isMaisRecente(String remoto, String local) {
        int[] r = parseVersion(limpar(remoto));
        int[] l = parseVersion(limpar(local));
        for (int i = 0; i < 3; i++) {
            if (r[i] > l[i]) return true;
            if (r[i] < l[i]) return false;
        }
        return false;
    }

    private String limpar(String v) {
        return v.replaceAll("[^0-9.]", "");
    }

    private int[] parseVersion(String v) {
        String[] parts = v.split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException ignored) { result[i] = 0; }
        }
        return result;
    }
}
