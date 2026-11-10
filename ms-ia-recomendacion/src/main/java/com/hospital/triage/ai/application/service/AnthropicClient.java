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

    private static final int TOKEN_CAP = 1800;
    private static final URI MESSAGES = URI.create("https://api.anthropic.com/v1/messages");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper json;

    @Value("${app.anthropic.api-key:}")
    private String apiKey;

    @Value("${app.anthropic.model:claude-haiku-5-5}")
    private String model;

    public AnthropicClient(ObjectMapper json) {
        this.json = json;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
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
            payload.put("model", model == null || model.isBlank() ? "claude-haiku-5-5" : model.trim());
            payload.put("max_tokens", maxTokens);
            payload.put("temperature", 0.1);
            payload.put("system", "Responde SOLO un JSON válido con el esquema que pide el mensaje del usuario. Sin markdown, sin texto alrededor.");
            payload.set("messages", json.createArrayNode().add(user));

            HttpRequest req = HttpRequest.newBuilder(MESSAGES)
                    .timeout(image != null ? Duration.ofSeconds(45) : Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey.trim())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                log.warn("Anthropic Haiku HTTP {}: {}", res.statusCode(), res.body());
                return Optional.empty();
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
                log.warn("Anthropic Haiku replied without text");
                return Optional.empty();
            }
            return Optional.of(json.readTree(extractJson(text)));
        } catch (Exception ex) {
            log.warn("Anthropic Haiku unavailable: {}", ex.getMessage());
            return Optional.empty();
        }
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
