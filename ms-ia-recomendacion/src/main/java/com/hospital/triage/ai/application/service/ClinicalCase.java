package com.hospital.triage.ai.application.service;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/** Caso clínico que la IA evalúa: signos, relato, datos opcionales y foto. */
@Builder
public record ClinicalCase(
        String conditionId,
        String reason,
        String history,
        int age,
        String sex,
        int heartRate,
        int spo2,
        int systolic,
        int diastolic,
        BigDecimal temperature,
        int respiratoryRate,
        Integer glucose,
        Integer painScale,
        String consciousness,
        List<String> estimatedFields,
        String photoBase64,
        String photoMime,
        double protocolScore,
        int news2,
        Double localModelScore,
        boolean pregnant,
        Integer gestationWeeks
) {
}
