/** AnthropicClient: primary clinical LLM. Gemini is fallback if this call is empty. */
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
import java.time.Duration;
import java.util.Optional;

/**
 * Claude Haiku first: faster and tighter on ESI than Gemini for this triage JSON.
 */
@Slf4j
@Component
public class AnthropicClient {

    private static final int TOKEN_CAP = 2500;
    private static final URI MESSAGES = URI.create("https://api.anthropic.com/v1/messages");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper json;

    @Value("${app.anthropic.api-key:}")
    private String apiKey;

    @Value("${app.anthropic.model:claude-haiku-5-5}")
    private String model;

    @Value("${app.anthropic.vision-model:claude-opus-5-5}")
    private String visionModel;

    public AnthropicClient(ObjectMapper json) {
        this.json = json;
    }

    private volatile String lastError = "";

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String lastError() {
        return lastError == null ? "" : lastError;
    }

    public Optional<JsonNode> generate(String prompt, String photoBase64, String photoMime, int maxOutputTokens) {
        if (!configured()) {
            return Optional.empty();
        }
        try {
            int maxTokens = Math.max(128, Math.min(maxOutputTokens, TOKEN_CAP));
            String image = GeminiClient.cleanBase64(photoBase64);
            var content = json.createArrayNode();
            if (image != null) {
                var source = json.createObjectNode();
                source.put("type", "base64");
                source.put("media_type", photoMime == null || photoMime.isBlank() ? "image/jpeg" : photoMime);
                source.put("data", image);
                var imageBlock = json.createObjectNode();
                imageBlock.put("type", "image");
                imageBlock.set("source", source);
                content.add(imageBlock);
            }
            content.add(json.createObjectNode().put("type", "text").put("text", prompt));
            var user = json.createObjectNode();
            user.put("role", "user");
            user.set("content", content);
            var payload = json.createObjectNode();
            String chosen = (image != null && visionModel != null && !visionModel.isBlank())
                    ? visionModel.trim()
                    : (model == null || model.isBlank() ? "claude-haiku-5-5" : model.trim());
            payload.put("model", chosen);
            payload.put("max_tokens", maxTokens);
            payload.put("system", image != null
                    ? "Eres médico de triage. PRIMERO mira la foto (lesiones, color, hinchazón, necrosis, sangrado). LUEGO el relato. LUEGO los signos. El menú del formulario no es el puntaje. Responde SOLO el JSON pedido."
                    : "Responde SOLO un JSON válido con el esquema que pide el mensaje del usuario. Sin markdown.");
            payload.set("messages", json.createArrayNode().add(user));

            HttpRequest req = HttpRequest.newBuilder(MESSAGES)
                    .timeout(image != null ? Duration.ofSeconds(70) : Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey.trim())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                lastError = "Anthropic " + chosen + " HTTP " + res.statusCode() + ": " + trimBody(res.body());
                log.warn("{}", lastError);
                if (image != null && visionModel != null && chosen.equals(visionModel.trim())
                        && model != null && !model.isBlank() && !chosen.equals(model.trim())) {
                    payload.put("model", model.trim());
                    HttpRequest retry = HttpRequest.newBuilder(MESSAGES)
                            .timeout(Duration.ofSeconds(45))
                            .header("Content-Type", "application/json")
                            .header("x-api-key", apiKey.trim())
                            .header("anthropic-version", "2023-06-01")
                            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                            .build();
                    res = http.send(retry, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() >= 400) {
                        lastError = "Anthropic fallback " + model + " HTTP " + res.statusCode() + ": " + trimBody(res.body());
                        log.warn("{}", lastError);
                        return Optional.empty();
                    }
                } else {
                    return Optional.empty();
                }
            }
            JsonNode root = json.readTree(res.body());
            String text = "";
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText()) && block.hasNonNull("text")) {
                    text = block.path("text").asText("");
                    break;
                }
            }
            if (text.isBlank()) {
                lastError = "Anthropic replied without text";
                log.warn("{}", lastError);
                return Optional.empty();
            }
            lastError = "";
            return Optional.of(json.readTree(extractJson(text)));
        } catch (Exception ex) {
            lastError = "Anthropic unavailable: " + ex.getMessage();
            log.warn("{}", lastError);
            return Optional.empty();
        }
    }

    private static String trimBody(String body) {
        if (body == null) {
            return "";
        }
        String t = body.replaceAll("\\s+", " ").trim();
        return t.length() > 280 ? t.substring(0, 280) : t;
    }

    private static String extractJson(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int start = t.indexOf('\n');
            int end = t.lastIndexOf("```");
            if (start > 0 && end > start) {
                t = t.substring(start + 1, end).trim();
            }
        }
        int from = t.indexOf('{');
        int to = t.lastIndexOf('}');
        if (from >= 0 && to > from) {
            return t.substring(from, to + 1);
        }
        return t;
    }
}
