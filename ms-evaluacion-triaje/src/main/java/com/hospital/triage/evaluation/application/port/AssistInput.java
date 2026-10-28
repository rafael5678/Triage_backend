package com.hospital.triage.evaluation.application.port;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/** Lo que evaluación le manda a la IA por gRPC. photoBase64 puede venir como data URL. */
@Builder
public record AssistInput(
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
        Double localModelScore
) {
}
