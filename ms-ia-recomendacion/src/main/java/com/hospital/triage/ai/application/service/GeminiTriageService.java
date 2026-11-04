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
                Ignora los signos ESTIMADOS: no los uses ni para subir ni para bajar.
                Signos MEDIDOS de emergencia (uno solo ya basta): SpO2 <=85 → 86-100; SpO2 70 es reanimación aunque el motivo sea un dedo. FC <40 → 86-100. Temp <=35 → mínimo 78. PAS <90 → mínimo 62. Varios a la vez → 90-100.
                Puntúa SOLO este ingreso. Si cambian oxígeno, pulso o temperatura, el score DEBE cambiar. No reutilices un 78 anterior.
                NUNCA copies el score del protocolo si no coincide con ESTOS números. SpO2 70 y FC 39 → 86-100. SpO2 98 y una herida en el dedo → 20-42, no crítica.
                El score del protocolo (%s) es referencia. Si los signos medidos empeoran, SUBE. Si están en rango y el motivo no es pecho/paro/derrame/trauma, no pongas 78.
                Gravedad de ESTE motivo en el catálogo del hospital: %s. Úsala en pecho, paro, derrame o trauma. En piel/dedo/cura con signos estables NO fuerces crítica.
                No inventes reanimación por un dolor de cabeza, un vómito o un dedo hinchado con signos de reposo.

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
                ageHint(c.age(), c.pregnant()),
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
        return ageHint(age, false);
    }

    static String ageHint(int age, boolean pregnant) {
        String base;
        if (age < 1) {
            base = "menor de 1 año: pulso 100-160, respiración 30-60, presión de arriba 70-100, oxígeno 95-100";
        } else if (age < 4) {
            base = age + " años (niño pequeño): pulso 90-150, respiración 24-40, presión 75-110, oxígeno 95-100";
        } else if (age < 6) {
            base = age + " años: pulso 80-140, respiración 22-34, presión 80-115, oxígeno 95-100";
        } else if (age < 12) {
            base = age + " años: pulso 70-120, respiración 18-30, presión 85-120, oxígeno 95-100";
        } else if (age < 16) {
            base = age + " años (adolescente): pulso 60-110, respiración 12-24, presión 90-125, oxígeno 95-100";
        } else if (age < 30) {
            base = age + " años (joven): pulso 60-100, respiración 12-20, presión 90-120, oxígeno 95-100";
        } else if (age < 45) {
            base = age + " años: pulso 60-100, respiración 12-20, presión 95-130, oxígeno 95-100";
        } else if (age < 60) {
            base = age + " años: pulso 60-95, respiración 12-20, presión 100-135, oxígeno 95-100";
        } else if (age < 70) {
            base = age + " años: pulso 60-90, respiración 12-22, presión 100-145, oxígeno 95-100";
        } else {
            base = age + " años (adulto mayor): pulso 55-90, respiración 12-22, presión 100-150, oxígeno 95-100";
        }
        if (!pregnant) {
            return base;
        }
        return base + ". EMBARAZADA: el pulso en reposo suele ir 10-20 más alto (hacia 80-110); respiración 14-22; oxígeno sigue 95-100; presión NO debe llegar a 140/90 (eso es preeclampsia, no lo normal).";
    }
}
