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
        Optional<JsonNode> answer = gemini.generate(
                prompt(c, catalog, chosen, hasPhoto),
                c.photoBase64(),
                c.photoMime(),
                hasPhoto ? 1600 : 1100);
        if (answer.isEmpty()) {
            return AiAssistDTO.builder().active(false).protocolScore(c.protocolScore()).build();
        }
        JsonNode parsed = answer.get();
        List<String> fromDataset = GeminiClient.texts(parsed.path("fromDataset"), 4);
        List<String> fromGemini = GeminiClient.texts(parsed.path("fromGemini"), 6);
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
        String photoBlock = hasPhoto
                ? "HAY FOTO. Mírala con detalle ANTES de puntuar: color de piel y labios, esfuerzo al respirar, heridas (tamaño, profundidad, sangrado, suciedad, humedad o sequedad), quemaduras, erupción, hinchazón, ojos, si parece consciente, sudor. Describe SOLO lo que se ve. Si está borrosa, dilo. Una foto de emergencia (sangrado activo, quemadura extensa, cianosis) puede subir el score aunque el motivo escrito sea leve."
                : "No hay foto. photoFindings vacío.";
        return """
                Eres apoyo de triage ESI en urgencias de Colombia. Español sencillo. No dramatizes ni inventes números.
                Analiza el caso COMPLETO junto: edad, sexo, motivo, relato, antecedentes, pulso, oxígeno, presión, temperatura, respiración, dolor, glucosa, conciencia y la foto si hay.
                Rangos normales para ESTA edad: %s.
                Un SpO2 mayor de 100 es el número del oxímetro; para saturación clínica usa min(100, SpO2). No lo trates como hipoxia.
                El pulso se acepta alto si lo midieron; contrástalo con el rango de la edad (en una niña de 9 años 70-120 en reposo).
                Si hay sudor, piel húmeda, mucosas secas o herida húmeda en el relato o la foto, úsalo.
                No le pidas al usuario la causa: tú das hipótesis clínicas (por ejemplo cefalea tensional vs alarma) según relato + signos + foto.
                Un solo número raro no basta para crítica. Varios graves a la vez, o emergencia en foto/relato con signos malos, sí.
                Ignora los signos ESTIMADOS: no los uses ni para subir ni para bajar.
                El score del protocolo (%s) es una base, no un techo: si el conjunto es grave, 78-100; si es leve (dolor de cabeza con signos de reposo y foto sin alarma), quédate cerca de esa base (mínima o baja).
                Gravedad de ESTE motivo en el catálogo del hospital: %s. Si es >=62 (Alta) o >=78 (Crítica), no bajes de esa banda.
                No inventes reanimación por un dolor de cabeza o un vómito con signos de reposo.

                JSON:
                {"score":0-100,
                 "dangerLead":"una frase: qué está mal o por qué no es grave",
                 "meaning":"qué implica en la cola, 1 frase",
                 "fromDataset":["máx 3, con los números reales vs la edad"],
                 "fromGemini":["máx 5: relato, foto y criterio clínico; hipótesis, no diagnóstico cerrado"],
                 "immediateActions":["máx 4, concretas"],
                 "testsToOrder":["máx 3"],
                 "monitoring":["máx 3"],
                 "redFlags":["máx 3: cuándo volver a triaje"],
                 "symptomExplanation":"4 a 6 frases: cada signo clave vs la edad, qué sugiere el relato, qué muestra la foto",
                 "suggestedArea":"REANIMACION | URGENCIAS_CRITICAS | URGENCIAS | CONSULTA_PRIORITARIA | CONSULTA_EXTERNA",
                 "photoFindings":"%s"}

                %s
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
                ageHint(c.age()),
                String.valueOf(c.protocolScore()),
                chosen == null ? "no está en el catálogo" : String.valueOf(chosen.getSeverity()),
                hasPhoto ? "3 a 6 frases de lo que VES en la foto" : "",
                HospitalDatasetPack.build(catalog),
                photoBlock,
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

    static String ageHint(int age) {
        if (age < 1) {
            return "menor de 1 año: pulso 100-160, respiración 30-60, presión de arriba 70-100, oxígeno 95-100";
        }
        if (age < 4) {
            return age + " años (niño pequeño): pulso 90-150, respiración 24-40, presión 75-110, oxígeno 95-100";
        }
        if (age < 6) {
            return age + " años: pulso 80-140, respiración 22-34, presión 80-115, oxígeno 95-100";
        }
        if (age < 12) {
            return age + " años: pulso 70-120, respiración 18-30, presión 85-120, oxígeno 95-100";
        }
        if (age < 16) {
            return age + " años (adolescente): pulso 60-110, respiración 12-24, presión 90-125, oxígeno 95-100";
        }
        if (age < 30) {
            return age + " años (joven): pulso 60-100, respiración 12-20, presión 90-120, oxígeno 95-100";
        }
        if (age < 45) {
            return age + " años: pulso 60-100, respiración 12-20, presión 95-130, oxígeno 95-100";
        }
        if (age < 60) {
            return age + " años: pulso 60-95, respiración 12-20, presión 100-135, oxígeno 95-100";
        }
        if (age < 70) {
            return age + " años: pulso 60-90, respiración 12-22, presión 100-145, oxígeno 95-100";
        }
        return age + " años (adulto mayor): pulso 55-90, respiración 12-22, presión 100-150, oxígeno 95-100";
    }
}
