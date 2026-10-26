package com.hospital.triage.evaluation.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.evaluation.application.port.PatientSnapshot;
import com.hospital.triage.evaluation.domain.entity.TriageEvaluation;
import com.hospital.triage.evaluation.domain.entity.VitalSigns;

import java.util.List;

final class RankedPatientAssembler {

    private RankedPatientAssembler() {
    }

    static RankedPatientDTO toDto(PatientSnapshot patient, TriageEvaluation evaluation, VitalSigns signs,
                                  Integer position, ObjectMapper json) {
        RankedPatientDTO.RankedPatientDTOBuilder builder = RankedPatientDTO.builder()
                .patientId(patient.id())
                .name(patient.name())
                .document(patient.document())
                .age(patient.age())
                .sex(patient.sex())
                .conditionId(patient.conditionId())
                .reason(patient.reason())
                .history(patient.history())
                .queuePosition(position);

        if (evaluation != null) {
            AiAssistDTO ia = readAi(evaluation.getAiExplanation(), json);
            builder.riskScore(evaluation.getRiskScore())
                    .triageLevel(evaluation.getTriageLevel())
                    .status(evaluation.getStatus())
                    .news2(evaluation.getNews2())
                    .evaluatedAt(evaluation.getEvaluatedAt())
                    .scoreSource(evaluation.getScoreSource())
                    .aiActive(ia.isActive())
                    .dangerLead(ia.getDangerLead())
                    .aiMeaning(ia.getMeaning())
                    .protocolScore(ia.getProtocolScore())
                    .geminiScore(ia.getSuggestedScore())
                    .aiReasons(ia.getReasons() == null ? List.of() : ia.getReasons())
                    .datasetReasons(ia.getDatasetReasons() == null ? List.of() : ia.getDatasetReasons())
                    .geminiReasons(ia.getGeminiReasons() == null ? List.of() : ia.getGeminiReasons());
        }

        if (signs != null) {
            builder.heartRate(signs.getHeartRate())
                    .spo2(signs.getSpo2())
                    .systolic(signs.getSystolic())
                    .diastolic(signs.getDiastolic())
                    .temperature(signs.getTemperature())
                    .respiratoryRate(signs.getRespiratoryRate());
        }
        return builder.build();
    }

    static String serializeAi(AiAssistDTO ia, ObjectMapper json) {
        try {
            return json.writeValueAsString(ia);
        } catch (Exception e) {
            return null;
        }
    }

    private static AiAssistDTO readAi(String raw, ObjectMapper json) {
        if (raw == null || raw.isBlank()) {
            return AiAssistDTO.builder().active(false).build();
        }
        try {
            return json.readValue(raw, AiAssistDTO.class);
        } catch (Exception e) {
            return AiAssistDTO.builder().active(false).build();
        }
    }
}
