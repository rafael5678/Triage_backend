package com.hospital.triage.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.ai.domain.entity.Symptom;
import com.hospital.triage.ai.infrastructure.persistence.SymptomRepository;
import com.hospital.triage.common.dto.AiAssistDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GeminiTriageService {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final ObjectMapper json;
    private final SymptomRepository symptoms;

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-3.8-flash,gemini-3.5-flash,gemini-3.5-flash-lite}")
    private String model;

    public GeminiTriageService(ObjectMapper json, SymptomRepository symptoms) {
        this.json = json;
        this.symptoms = symptoms;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public AiAssistDTO assist(String conditionId, String reason, String history, int age, String sex,
                              int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                              int respiratoryRate, double protocolScore, int news2, Double localModelScore) {
        if (!configured()) {
            return AiAssistDTO.builder().active(false).build();
        }
        try {
            var catalog = symptoms.findByActiveTrueOrderBySeverityDesc();
            Symptom chosen = conditionId == null ? null : symptoms.findById(conditionId).orElse(null);
            String prompt = """
                    Eres el segundo cerebro de un triage de urgencias en español.
                    Debes usar LAS DOS inteligencias a la vez:
                    1) DATASET/PROTOCOLO del hospital (fórmula NEWS2, catálogo, ejemplos). No lo ignores.
                    2) TU conocimiento clínico (ESI, SCA, ACV, sepsis, anafilaxia, shock, hipoxia, abdomen agudo).
                    Si el relato o los signos encajan con una emergencia que el score numérico subestima, SÚBELO.
                    Nunca bajes el score si hay hipoxia, hipotensión, FR extrema, FC extrema o paro.
                    No inventes signos que no estén en el caso.

                    Responde SOLO JSON:
                    {"score":0-100,"dangerLead":"frase corta","meaning":"qué implica para la cola","fromDataset":["...","..."],"fromGemini":["...","..."]}
                    fromDataset: por qué el protocolo/dataset da ese riesgo (máximo 3).
                    fromGemini: qué aporta tu criterio clínico extra (máximo 3). Lenguaje sencillo.

                    %s

                    CASO ACTUAL:
                    Motivo: %s (%s)
                    Relato: %s
                    Antecedentes: %s
                    Edad: %d  Sexo: %s
                    FC:%d  SpO2:%d  PAS:%d  PAD:%d  Temp:%s  FR:%d
                    NEWS2:%d  Score del protocolo/dataset:%s  Score del modelo local entrenado:%s
                    """.formatted(
                    HospitalDatasetPack.build(catalog),
                    conditionId == null ? "" : conditionId,
                    chosen == null ? "" : chosen.getLabel(),
                    reason == null ? "" : reason,
                    history == null ? "" : history,
                    age, sex == null ? "" : sex,
                    heartRate, spo2, systolic, diastolic, temperature, respiratoryRate,
                    news2, protocolScore,
                    localModelScore == null ? "no disponible" : String.valueOf(localModelScore)
            );

            var payload = json.createObjectNode();
            var parts = json.createArrayNode();
            parts.add(json.createObjectNode().put("text", prompt));
            var content = json.createObjectNode();
            content.set("parts", parts);
            payload.set("contents", json.createArrayNode().add(content));
            var gen = json.createObjectNode();
            gen.put("temperature", 0.15);
            // Los modelos Gemini 3 gastan tokens de salida pensando; con 512 la respuesta llega cortada.
            gen.put("maxOutputTokens", 2048);
            gen.put("responseMimeType", "application/json");
            payload.set("generationConfig", gen);
            String body = json.writeValueAsString(payload);

            HttpResponse<String> res = null;
            for (String candidate : models()) {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + candidate + ":generateContent";
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
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
                return AiAssistDTO.builder().active(false).build();
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
                return AiAssistDTO.builder().active(false).build();
            }
            JsonNode parsed = json.readTree(text);
            List<String> fromDataset = texts(parsed.path("fromDataset"));
            List<String> fromGemini = texts(parsed.path("fromGemini"));
            List<String> reasons = new ArrayList<>();
            reasons.addAll(fromDataset);
            reasons.addAll(fromGemini);
            if (reasons.isEmpty()) {
                reasons.addAll(texts(parsed.path("reasons")));
            }
            return AiAssistDTO.builder()
                    .active(true)
                    .suggestedScore(parsed.path("score").isNumber() ? parsed.path("score").asDouble() : null)
                    .dangerLead(parsed.path("dangerLead").asText(null))
                    .meaning(parsed.path("meaning").asText(null))
                    .reasons(reasons)
                    .datasetReasons(fromDataset)
                    .geminiReasons(fromGemini)
                    .protocolScore(protocolScore)
                    .localModelScore(localModelScore)
                    .build();
        } catch (Exception ex) {
            log.warn("Gemini no disponible, se usa solo el protocolo: {}", ex.getMessage());
            return AiAssistDTO.builder().active(false).build();
        }
    }

    /** GEMINI_MODEL admite varios modelos separados por coma; si uno está saturado se prueba el siguiente. */
    private List<String> models() {
        List<String> out = new ArrayList<>();
        if (model != null) {
            for (String name : model.split(",")) {
                if (!name.isBlank()) {
                    out.add(name.trim());
                }
            }
        }
        if (out.isEmpty()) {
            out.add("gemini-3.8-flash");
        }
        return out;
    }

    private static List<String> texts(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                if (n.isTextual() && !n.asText().isBlank()) {
                    out.add(n.asText());
                }
            });
        }
        return out;
    }
}
