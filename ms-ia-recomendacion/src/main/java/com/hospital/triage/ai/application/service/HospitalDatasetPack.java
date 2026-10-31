package com.hospital.triage.ai.application.service;

import com.hospital.triage.ai.domain.entity.Symptom;

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
                PROTOCOLO: score 0-100. Motivo + NEWS2 + edad + todos los signos medidos juntos. ESI: >=78 I, >=62 II, >=42 III, >=22 IV, resto V.
                No marques crítica por un solo signo. Integra edad, motivo y el resto de números.
                Un signo ESTIMADO se ignora. Dolor de cabeza o vómito con signos de reposo = III o menos, salvo rigidez de cuello, peor cefalea de la vida, o varios signos medidos graves.
                Catálogo:
                - %s
                """.formatted(symptoms);
    }
}
