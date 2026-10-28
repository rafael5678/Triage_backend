package com.hospital.triage.evaluation.application.port;

import java.util.UUID;

public record PatientSnapshot(
        UUID id,
        String name,
        String document,
        Integer age,
        String sex,
        String conditionId,
        String reason,
        String history,
        String phone,
        boolean pregnant,
        Integer gestationWeeks
) {
    public PatientSnapshot(UUID id, String name, String document, Integer age, String sex,
                           String conditionId, String reason, String history) {
        this(id, name, document, age, sex, conditionId, reason, history, null, false, null);
    }

    public static PatientSnapshot unknown(UUID id) {
        return new PatientSnapshot(id, "Paciente no disponible", "", null, null, null, null, null);
    }
}
