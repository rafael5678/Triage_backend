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
                El catálogo fija piso en pecho, paro, derrame o trauma. Un motivo de piel/dedo con signos estables NO queda en 78.
                Integra edad, motivo, relato, foto y TODOS los números MEDIDOS de ESTE caso. Si cambian los números, cambia el score.
                SpO2 <=85, FC <40 o temp <=34 medidos = ESI I (86-100) aunque el motivo sea piel o un dedo.
                Un signo ESTIMADO se ignora. Dolor de cabeza o vómito con signos de reposo = III o menos, salvo alarma en foto o signos medidos graves.
                Catálogo:
                - %s
                """.formatted(symptoms);
    }
}
