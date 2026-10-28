package com.hospital.triage.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Llamada a la API de Gemini con respuesta JSON, foto opcional y modelo de respaldo.
 * La clave se comparte con otras aplicaciones, así que se cuida la cuota: respuestas repetidas salen
 * de memoria, hay tope diario y por minuto, y como mucho se prueban dos modelos por pregunta.
 */
@Slf4j
@Component
public class GeminiClient {

    private static final int MAX_MODELS_PER_CALL = 2;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final int CACHE_SIZE = 200;
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final ObjectMapper json;
    private Clock clock = Clock.systemUTC();

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-3.5-flash-lite,gemini-3.1-flash-lite}")
    private String model;

    @Value("${app.gemini.daily-limit:150}")
    private int dailyLimit = 150;

    @Value("${app.gemini.per-minute-limit:6}")
    private int perMinuteLimit = 6;

    private record Cached(JsonNode value, Instant at) {
    }

    private final Map<String, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private final ArrayDeque<Instant> lastMinute = new ArrayDeque<>();
    private LocalDate day;
    private int usedToday;

    public GeminiClient(ObjectMapper json) {
        this.json = json;
    }

    /** Llamadas reales a Gemini hechas hoy (las que salieron de memoria no cuentan). */
    public synchronized int usedToday() {
        rollDay();
        return usedToday;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Devuelve el JSON que generó el modelo, o vacío si Gemini no está configurado o ningún modelo respondió.
     * photoBase64 puede venir como data URL; se le quita el prefijo.
     */
    public Optional<JsonNode> generate(String prompt, String photoBase64, String photoMime, int maxOutputTokens) {
        if (!configured()) {
            return Optional.empty();
        }
        try {
            var payload = json.createObjectNode();
            var parts = json.createArrayNode();
            String image = cleanBase64(photoBase64);
            if (image != null) {
                var inline = json.createObjectNode();
                inline.put("mimeType", photoMime == null || photoMime.isBlank() ? "image/jpeg" : photoMime);
                inline.put("data", image);
                parts.add(json.createObjectNode().set("inlineData", inline));
            }
            parts.add(json.createObjectNode().put("text", prompt));
            var content = json.createObjectNode();
            content.put("role", "user");
            content.set("parts", parts);
            payload.set("contents", json.createArrayNode().add(content));
            var gen = json.createObjectNode();
            gen.put("temperature", 0.15);
            // Los modelos Gemini 3 gastan tokens de salida pensando; con pocos tokens la respuesta llega cortada.
            gen.put("maxOutputTokens", maxOutputTokens);
            gen.put("responseMimeType", "application/json");
            payload.set("generationConfig", gen);
            String body = json.writeValueAsString(payload);

            String key = sha256(body);
            JsonNode remembered = fromCache(key);
            if (remembered != null) {
                return Optional.of(remembered);
            }
            HttpResponse<String> res = null;
            for (String candidate : models()) {
                if (!takeQuota()) {
                    log.warn("Tope de llamadas a Gemini alcanzado ({} por día, {} por minuto); se usa solo el protocolo",
                            dailyLimit, perMinuteLimit);
                    break;
                }
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + candidate + ":generateContent";
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", apiKey.trim())
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() < 400) {
                    break;
                }
                log.warn("Gemini {} HTTP {}: {}", candidate, res.statusCode(), res.body());
                if (res.statusCode() == 400 || res.statusCode() == 401 || res.statusCode() == 403) {
                    break;
                }
            }
            if (res == null || res.statusCode() >= 400) {
                return Optional.empty();
            }
            JsonNode root = json.readTree(res.body());
            String text = "";
            for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.hasNonNull("text")) {
                    text = part.path("text").asText("");
                    break;
                }
            }
            if (text.isBlank()) {
                log.warn("Gemini respondió sin texto: {}", root.path("candidates").path(0).path("finishReason").asText(""));
                return Optional.empty();
            }
            JsonNode parsed = json.readTree(stripFence(text));
            remember(key, parsed);
            return Optional.of(parsed);
        } catch (Exception ex) {
            log.warn("Gemini no disponible: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** GEMINI_MODEL admite varios modelos separados por coma; si uno está saturado se prueba el siguiente (máximo 2). */
    private List<String> models() {
        List<String> out = new ArrayList<>();
        if (model != null) {
            for (String name : model.split(",")) {
                if (!name.isBlank() && out.size() < MAX_MODELS_PER_CALL) {
                    out.add(name.trim());
                }
            }
        }
        if (out.isEmpty()) {
            out.add("gemini-3.5-flash-lite");
        }
        return out;
    }

    private synchronized JsonNode fromCache(String key) {
        Cached hit = cache.get(key);
        if (hit == null) {
            return null;
        }
        if (Duration.between(hit.at(), clock.instant()).compareTo(CACHE_TTL) > 0) {
            cache.remove(key);
            return null;
        }
        return hit.value();
    }

    private synchronized void remember(String key, JsonNode value) {
        cache.put(key, new Cached(value, clock.instant()));
    }

    private synchronized boolean takeQuota() {
        rollDay();
        Instant now = clock.instant();
        while (!lastMinute.isEmpty() && Duration.between(lastMinute.peekFirst(), now).toSeconds() >= 60) {
            lastMinute.pollFirst();
        }
        if (usedToday >= dailyLimit || lastMinute.size() >= perMinuteLimit) {
            return false;
        }
        usedToday++;
        lastMinute.addLast(now);
        return true;
    }

    private void rollDay() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZONE);
        if (!today.equals(day)) {
            day = today;
            usedToday = 0;
        }
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    static String cleanBase64(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int comma = raw.indexOf(',');
        String data = raw.startsWith("data:") && comma > 0 ? raw.substring(comma + 1) : raw;
        return data.isBlank() ? null : data.trim();
    }

    private static String stripFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int start = t.indexOf('\n');
            int end = t.lastIndexOf("```");
            if (start > 0 && end > start) {
                return t.substring(start + 1, end).trim();
            }
        }
        return t;
    }

    static List<String> texts(JsonNode node, int max) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode n : node) {
                if (n.isTextual() && !n.asText().isBlank() && out.size() < max) {
                    out.add(n.asText().trim());
                }
            }
        }
        return out;
    }

    static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isBlank() ? null : value;
    }
}
