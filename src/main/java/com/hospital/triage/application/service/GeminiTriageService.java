package com.hospital.triage.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.application.dto.AsistenciaIaDTO;
import com.hospital.triage.domain.entity.Symptom;
import com.hospital.triage.infrastructure.persistence.SymptomRepository;
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
import java.util.stream.Collectors;

@Slf4j
@Service
public class GeminiTriageService {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final ObjectMapper json;
    private final SymptomRepository symptoms;

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-2.0-flash}")
    private String model;

    public GeminiTriageService(ObjectMapper json, SymptomRepository symptoms) {
        this.json = json;
        this.symptoms = symptoms;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public AsistenciaIaDTO asistir(String lesionId, String motivo, String antecedentes, int edad, String sexo,
                                  int fc, int spo2, int pas, int pad, BigDecimal temp, int fr,
                                  double scoreProtocolo, int news2) {
        if (!configured()) {
            return AsistenciaIaDTO.builder().activa(false).build();
        }
        try {
            String catalog = symptoms.findByActiveTrueOrderBySeverityDesc().stream()
                    .limit(24)
                    .map(s -> s.getId() + "|" + s.getLabel() + "|" + s.getSeverity())
                    .collect(Collectors.joining("; "));
            Symptom chosen = lesionId == null ? null : symptoms.findById(lesionId).orElse(null);
            String prompt = """
                    Eres apoyo de triage de urgencias (ESI). No sustituyes al clínico.
                    Usa el protocolo local Y conocimiento clínico (SCA, ACV, sepsis, anafilaxia, shock, hipoxia).
                    El score local ya existe: no lo bajes si hay hipoxia, hipotensión, FR extrema, FC extrema o paro.
                    Responde SOLO JSON: {"score":0-100,"dangerLead":"frase corta en español","meaning":"qué implica para la cola","reasons":["...","..."]}
                    Máximo 5 reasons, lenguaje sencillo para personal de admisión.

                    Catálogo del hospital (id|texto|gravedad): %s
                    Motivo elegido: %s (%s)
                    Relato: %s
                    Antecedentes: %s
                    Edad: %d  Sexo: %s
                    FC:%d  SpO2:%d  PAS:%d  PAD:%d  Temp:%s  FR:%d
                    NEWS2:%d  Score protocolo:%s
                    """.formatted(
                    catalog,
                    lesionId == null ? "" : lesionId,
                    chosen == null ? "" : chosen.getLabel(),
                    motivo == null ? "" : motivo,
                    antecedentes == null ? "" : antecedentes,
                    edad, sexo == null ? "" : sexo,
                    fc, spo2, pas, pad, temp, fr,
                    news2, scoreProtocolo
            );

            var payload = json.createObjectNode();
            var parts = json.createArrayNode();
            parts.add(json.createObjectNode().put("text", prompt));
            var content = json.createObjectNode();
            content.set("parts", parts);
            payload.set("contents", json.createArrayNode().add(content));
            var gen = json.createObjectNode();
            gen.put("temperature", 0.15);
            gen.put("maxOutputTokens", 512);
            gen.put("responseMimeType", "application/json");
            payload.set("generationConfig", gen);
            String body = json.writeValueAsString(payload);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + apiKey.trim();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                log.warn("Gemini HTTP {}: {}", res.statusCode(), res.body());
                return AsistenciaIaDTO.builder().activa(false).build();
            }
            JsonNode root = json.readTree(res.body());
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                return AsistenciaIaDTO.builder().activa(false).build();
            }
            JsonNode parsed = json.readTree(text);
            List<String> reasons = new ArrayList<>();
            if (parsed.path("reasons").isArray()) {
                parsed.path("reasons").forEach(n -> {
                    if (n.isTextual() && !n.asText().isBlank()) reasons.add(n.asText());
                });
            }
            return AsistenciaIaDTO.builder()
                    .activa(true)
                    .scoreSugerido(parsed.path("score").isNumber() ? parsed.path("score").asDouble() : null)
                    .dangerLead(parsed.path("dangerLead").asText(null))
                    .meaning(parsed.path("meaning").asText(null))
                    .reasons(reasons)
                    .build();
        } catch (Exception ex) {
            log.warn("Gemini no disponible, se usa solo el protocolo: {}", ex.getMessage());
            return AsistenciaIaDTO.builder().activa(false).build();
        }
    }
}
