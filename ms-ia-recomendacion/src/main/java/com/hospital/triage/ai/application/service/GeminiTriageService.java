package com.hospital.triage.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.triage.ai.domain.entity.Symptom;
import com.hospital.triage.ai.infrastructure.persistence.SymptomRepository;
import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.enums.CareArea;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GeminiTriageService {

    private final GeminiClient gemini;
    private final SymptomRepository symptoms;

    public GeminiTriageService(GeminiClient gemini, SymptomRepository symptoms) {
        this.gemini = gemini;
        this.symptoms = symptoms;
    }

    public boolean configured() {
        return gemini.configured();
    }

    public AiAssistDTO assist(ClinicalCase c) {
        if (!gemini.configured()) {
            return AiAssistDTO.builder().active(false).build();
        }
        var catalog = symptoms.findByActiveTrueOrderBySeverityDesc();
        Symptom chosen = c.conditionId() == null ? null : symptoms.findById(c.conditionId()).orElse(null);
        boolean hasPhoto = GeminiClient.cleanBase64(c.photoBase64()) != null;
        Optional<JsonNode> answer = gemini.generate(prompt(c, catalog, chosen, hasPhoto), c.photoBase64(), c.photoMime(), 2048);
        if (answer.isEmpty()) {
            return AiAssistDTO.builder().active(false).protocolScore(c.protocolScore()).build();
        }
        JsonNode parsed = answer.get();
        List<String> fromDataset = GeminiClient.texts(parsed.path("fromDataset"), 3);
        List<String> fromGemini = GeminiClient.texts(parsed.path("fromGemini"), 3);
        List<String> reasons = new ArrayList<>(fromDataset);
        reasons.addAll(fromGemini);
        if (reasons.isEmpty()) {
            reasons.addAll(GeminiClient.texts(parsed.path("reasons"), 6));
        }
        CareArea area = CareArea.parse(GeminiClient.textOrNull(parsed.path("suggestedArea")));
        return AiAssistDTO.builder()
                .active(true)
                .suggestedScore(parsed.path("score").isNumber() ? parsed.path("score").asDouble() : null)
                .dangerLead(GeminiClient.textOrNull(parsed.path("dangerLead")))
                .meaning(GeminiClient.textOrNull(parsed.path("meaning")))
                .reasons(reasons)
                .datasetReasons(fromDataset)
                .geminiReasons(fromGemini)
                .immediateActions(GeminiClient.texts(parsed.path("immediateActions"), 6))
                .testsToOrder(GeminiClient.texts(parsed.path("testsToOrder"), 6))
                .monitoring(GeminiClient.texts(parsed.path("monitoring"), 5))
                .redFlags(GeminiClient.texts(parsed.path("redFlags"), 5))
                .symptomExplanation(GeminiClient.textOrNull(parsed.path("symptomExplanation")))
                .suggestedArea(area == null ? null : area.name())
                .photoFindings(hasPhoto ? GeminiClient.textOrNull(parsed.path("photoFindings")) : null)
                .protocolScore(c.protocolScore())
                .localModelScore(c.localModelScore())
                .build();
    }

    private String prompt(ClinicalCase c, List<Symptom> catalog, Symptom chosen, boolean hasPhoto) {
        String estimated = c.estimatedFields() == null || c.estimatedFields().isEmpty()
                ? "ninguno (todos medidos)"
                : String.join(", ", c.estimatedFields()) + " (ESTIMADOS por IA, no medidos: no los uses para bajar el riesgo)";
        return """
                Eres el segundo cerebro de un triage de urgencias en Colombia. Hablas español sencillo.
                Usa LAS DOS inteligencias a la vez:
                1) DATASET/PROTOCOLO del hospital (NEWS2, catálogo, ejemplos). No lo ignores.
                2) TU conocimiento clínico (ESI/Manchester, SCA, ACV, sepsis, anafilaxia, shock, hipoxia, hipoglucemia, abdomen agudo, trauma).
                Si el relato, la foto o los signos encajan con una emergencia que el score numérico subestima, SÚBELO.
                Nunca bajes el score si hay hipoxia, hipotensión, FR extrema, FC extrema, conciencia alterada, glucosa extrema o paro.
                No inventes datos que no estén en el caso.

                Responde SOLO este JSON:
                {"score":0-100,
                 "dangerLead":"frase corta de por qué es peligroso o no",
                 "meaning":"qué implica para la cola",
                 "fromDataset":["por qué el protocolo da ese riesgo (máx 3)"],
                 "fromGemini":["qué aporta tu criterio clínico (máx 3)"],
                 "immediateActions":["qué hacer YA, concreto: monitor, oxígeno, acceso venoso, ECG en 10 min... (máx 6)"],
                 "testsToOrder":["exámenes a pedir: glucometría, troponina, hemograma, gases, Rx, TAC... (máx 6)"],
                 "monitoring":["qué vigilar y cada cuánto (máx 5)"],
                 "redFlags":["señales que obligan a subirlo de inmediato (máx 5)"],
                 "symptomExplanation":"por qué estos síntomas y signos importan, en 2 a 4 frases sencillas",
                 "suggestedArea":"REANIMACION | URGENCIAS_CRITICAS | URGENCIAS | CONSULTA_PRIORITARIA | CONSULTA_EXTERNA",
                 "photoFindings":"%s"}

                %s

                CASO ACTUAL:
                Motivo: %s (%s)
                Relato: %s
                Antecedentes: %s
                Edad: %d  Sexo: %s%s%s
                FC:%d  SpO2:%d  PAS:%d  PAD:%d  Temp:%s  FR:%d
                Glucosa (mg/dL): %s  Dolor 0-10: %s  Conciencia AVPU: %s
                Signos estimados: %s
                NEWS2:%d  Score del protocolo/dataset:%s
                """.formatted(
                hasPhoto ? "qué se ve en la foto adjunta y qué tan grave parece (herida, quemadura, deformidad, color de piel, sangrado)" : "",
                HospitalDatasetPack.build(catalog),
                c.conditionId() == null ? "" : c.conditionId(),
                chosen == null ? "" : chosen.getLabel(),
                c.reason() == null ? "" : c.reason(),
                c.history() == null ? "" : c.history(),
                c.age(), c.sex() == null ? "" : c.sex(),
                !c.pregnant() ? "" : "  EMBARAZADA" + (c.gestationWeeks() == null ? "" : " de " + c.gestationWeeks() + " semanas")
                        + " (considera preeclampsia, hemorragia obstétrica, bienestar fetal)",
                c.age() < 16 ? "\n                NIÑO/ADOLESCENTE: usa rangos pediátricos para su edad, no los de adulto." : "",
                c.heartRate(), c.spo2(), c.systolic(), c.diastolic(), c.temperature(), c.respiratoryRate(),
                c.glucose() == null ? "no medida" : c.glucose(),
                c.painScale() == null ? "no dicho" : c.painScale(),
                c.consciousness() == null || c.consciousness().isBlank() ? "no dicho" : c.consciousness(),
                estimated,
                c.news2(), c.protocolScore()
        );
    }
}
