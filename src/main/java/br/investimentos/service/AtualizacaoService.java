package br.investimentos.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AtualizacaoService {

    private static final String API_BRANCH_URL =
        "https://api.github.com/repos/Rui-Goncalves-II/Sifin/branches/main";
    private static final String COMMITS_URL =
        "https://github.com/Rui-Goncalves-II/Sifin/commits/main";

    private String commitLocalCache;

    public static String getReleaseUrl() {
        return COMMITS_URL;
    }

    public String getCommitLocalCache() {
        return commitLocalCache;
    }

    private String getCommitLocal() {
        String appHome = System.getProperty("app.home");
        if (appHome == null) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "-C", appHome, "rev-parse", "HEAD");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String hash = new String(proc.getInputStream().readAllBytes()).trim();
            proc.waitFor();
            return hash.isEmpty() ? null : hash;
        } catch (Exception e) {
            return null;
        }
    }

    /** Verifica assincronamente. Chama onAtualizacaoDisponivel(commitRemotoShort) se houver commit mais recente. */
    public void verificarAtualizacaoAsync(Consumer<String> onAtualizacaoDisponivel) {
        CompletableFuture.runAsync(() -> {
            try {
                String localHash = getCommitLocal();
                commitLocalCache = localHash;
                if (localHash == null) return;

                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BRANCH_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Sifin-App")
                    .build();
                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String remoteSha = extrairSha(response.body());
                    if (remoteSha != null && !remoteSha.equalsIgnoreCase(localHash)) {
                        onAtualizacaoDisponivel.accept(remoteSha.substring(0, 7));
                    }
                }
            } catch (Exception ignored) {
                // sem rede — ignora silenciosamente
            }
        });
    }

    private String extrairSha(String json) {
        // JSON: {"name":"main","commit":{"sha":"abcdef..."},...}
        int idx = json.indexOf("\"commit\"");
        if (idx < 0) return null;
        int shaIdx = json.indexOf("\"sha\"", idx);
        if (shaIdx < 0) return null;
        int colon = json.indexOf(':', shaIdx);
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }
}
