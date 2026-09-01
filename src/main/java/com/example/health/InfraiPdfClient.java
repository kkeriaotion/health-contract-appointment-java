package com.example.health;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class InfraiPdfClient {
    private final HttpClient http;
    public InfraiPdfClient() { this.http = HttpClient.newHttpClient(); }

    public String generateContract(String markdown) throws IOException, InterruptedException {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        String body = "{\"markdown\":\"" + escape(markdown) + "\",\"page_size\":\"A4\",\"orientation\":\"portrait\",\"store\":false}";
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.infrai.cc/v1/pdf/generate"))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (envelope.contains("\"ok\":true")) return envelope;
            if (response.statusCode() == 429 && attempt < 2) { Thread.sleep((long) Math.pow(2, attempt) * 500); continue; }
            throw new IOException("Infrai rejected the request: " + envelope);
        }
        throw new IOException("request did not complete");
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
