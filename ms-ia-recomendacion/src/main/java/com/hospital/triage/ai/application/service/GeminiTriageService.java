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
        Optional<JsonNode> answer = gemini.generate(prompt(c, catalog, chosen, hasPhoto), c.photoBase64(), c.photoMime(), 700);
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
                : String.join(", ", c.estimatedFields()) + " (NO MEDIDOS: no inventes shock ni reanimación)";
        return """
                Eres apoyo de triage. Español sencillo. No dramatizes.
                El score que cuentes debe acercarse al del protocolo (%s) si los signos están estimados o son de reposo.
                Solo sube el riesgo si hay un número MEDIDO grave o un relato de emergencia real (dolor de pecho, habla rara, no responde, sangrado abundante).
                No inventes shock, paro ni reanimación por un dolor de cabeza o un vómito.

                JSON:
                {"score":0-100,
                 "dangerLead":"una frase: qué está mal o por qué no es grave",
                 "meaning":"qué implica en la cola, 1 frase",
                 "fromDataset":["máx 2, con los números reales"],
                 "fromGemini":["máx 2, criterio clínico del motivo"],
                 "immediateActions":["máx 4, concretas"],
                 "testsToOrder":["máx 3"],
                 "monitoring":["máx 3"],
                 "redFlags":["máx 3: cuándo volver a triaje"],
                 "symptomExplanation":"2 frases: por qué este motivo importa y qué dicen estos signos",
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
                String.valueOf(c.protocolScore()),
                hasPhoto ? "qué se ve en la foto, breve" : "",
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
