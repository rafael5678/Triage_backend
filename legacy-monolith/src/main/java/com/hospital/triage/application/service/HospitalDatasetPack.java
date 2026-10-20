package com.hospital.triage.application.service;

import com.hospital.triage.domain.entity.Symptom;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Compact card of the hospital scoring dataset sent to Gemini on every case.
 * Mirrors frontend ml/dataset.js + conditions.js without shipping 140 raw rows.
 */
final class HospitalDatasetPack {

    private HospitalDatasetPack() {}

    static String build(List<Symptom> catalog) {
        String symptoms = catalog.stream()
                .map(s -> s.getId() + "=" + s.getLabel() + " (gravedad " + s.getSeverity() + ")")
                .collect(Collectors.joining("\n- "));
        return """
                DATASET / PROTOCOLO DEL HOSPITAL (esto es nuestra inteligencia local):
                Score 0-100. Fórmula: gravedad_motivo*0.38 + (NEWS2/20)*100*0.42 + min(25,(shock-0.5)*40) + min(12,edad/10)*0.35 + comorb*0.55
                shock = FC / max(40, PAS)
                Piso de emergencia: SpO2<85 o PAS<80 o FR<8 o FC>150 => mínimo 86. Motivo paro => mínimo 97.
                NEWS2: FR, SpO2, temperatura, PAS, FC (0 a 20).
                ESI por score: >=78 I, >=62 II, >=42 III, >=22 IV, resto V.
                Antecedentes (peso): iam/infarto 10, icc/falla 9, epoc/asma 8, renal 7, inmuno/vih 7, cancer 6, dm 4, hta 3.

                Catálogo vivo (el admin puede cambiarlo):
                - %s

                Ejemplos del dataset de entrenamiento (mismos pesos; no son pacientes reales):
                - paro, SpO2 78, PAS 70, FR 6, FC 40, 70 años => ~97-100 (reanimación)
                - sca, SpO2 93, PAS 88, FR 24, FC 128, 68 años, iam => ~78-90 (dolor de pecho + signos malos)
                - avc, habla rara, PAS 200, 74 años => ~80-94
                - sepsis, fiebre 39.6, SpO2 91, FC 125, 81 años => ~70-88
                - disnea, SpO2 88, FR 32, epoc => ~75-86
                - fractura, signos normales, 30 años => ~20-40
                - gi, vómito, estable => ~10-25
                - cura, herida menor => ~5-15
                """.formatted(symptoms);
    }
}
