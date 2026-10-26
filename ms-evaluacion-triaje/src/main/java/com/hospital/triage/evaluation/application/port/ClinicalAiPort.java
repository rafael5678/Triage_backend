package com.hospital.triage.evaluation.application.port;

import com.hospital.triage.common.dto.AiAssistDTO;

import java.math.BigDecimal;

public interface ClinicalAiPort {
    AiAssistDTO assist(String conditionId, String reason, String history, int age, String sex,
                       int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                       int respiratoryRate, double protocolScore, int news2, Double localModelScore);

    boolean configured();
}
