package com.hospital.triage.grpc.mapping;

import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;
import com.hospital.triage.grpc.v1.RankedPatient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.UUID;

public final class RankedPatientMessages {

    private RankedPatientMessages() {
    }

    public static RankedPatient toProto(RankedPatientDTO dto) {
        RankedPatient.Builder builder = RankedPatient.newBuilder()
                .setPatientId(dto.getPatientId() == null ? "" : dto.getPatientId().toString())
                .setName(text(dto.getName()))
                .setDocument(text(dto.getDocument()))
                .setAge(dto.getAge() == null ? 0 : dto.getAge())
                .setSex(text(dto.getSex()))
                .setConditionId(text(dto.getConditionId()))
                .setReason(text(dto.getReason()))
                .setHistory(text(dto.getHistory()))
                .setTriageLevel(dto.getTriageLevel() == null ? "" : dto.getTriageLevel().name())
                .setStatus(dto.getStatus() == null ? "" : dto.getStatus().name())
                .setEvaluatedAt(dto.getEvaluatedAt() == null ? "" : dto.getEvaluatedAt().toString())
                .setScoreSource(text(dto.getScoreSource()))
                .setAiActive(Boolean.TRUE.equals(dto.getAiActive()))
                .setDangerLead(text(dto.getDangerLead()))
                .setAiMeaning(text(dto.getAiMeaning()));

        if (dto.getRiskScore() != null) {
            builder.setRiskScore(dto.getRiskScore().doubleValue());
        }
        if (dto.getQueuePosition() != null) {
            builder.setQueuePosition(dto.getQueuePosition());
        }
        if (dto.getNews2() != null) {
            builder.setNews2(dto.getNews2());
        }
        if (dto.getHeartRate() != null || dto.getSpo2() != null || dto.getTemperature() != null) {
            builder.setHasVitals(true)
                    .setHeartRate(value(dto.getHeartRate()))
                    .setSpo2(value(dto.getSpo2()))
                    .setSystolic(value(dto.getSystolic()))
                    .setDiastolic(value(dto.getDiastolic()))
                    .setRespiratoryRate(value(dto.getRespiratoryRate()));
            if (dto.getTemperature() != null) {
                builder.setTemperature(dto.getTemperature().doubleValue());
            }
        }
        if (dto.getProtocolScore() != null) {
            builder.setProtocolScore(dto.getProtocolScore());
        }
        if (dto.getGeminiScore() != null) {
            builder.setGeminiScore(dto.getGeminiScore());
        }
        if (dto.getAiReasons() != null) {
            builder.addAllAiReasons(dto.getAiReasons());
        }
        if (dto.getDatasetReasons() != null) {
            builder.addAllDatasetReasons(dto.getDatasetReasons());
        }
        if (dto.getGeminiReasons() != null) {
            builder.addAllGeminiReasons(dto.getGeminiReasons());
        }
        if (dto.getGlucose() != null) {
            builder.setGlucose(dto.getGlucose());
        }
        if (dto.getPainScale() != null) {
            builder.setPainScale(dto.getPainScale());
        }
        if (dto.getMaxWaitMinutes() != null) {
            builder.setMaxWaitMinutes(dto.getMaxWaitMinutes());
        }
        builder.setConsciousness(text(dto.getConsciousness()))
                .setSymptomExplanation(text(dto.getSymptomExplanation()))
                .setPhotoFindings(text(dto.getPhotoFindings()))
                .setDestinationArea(text(dto.getDestinationArea()))
                .setDestinationCode(text(dto.getDestinationCode()))
                .setDestinationReason(text(dto.getDestinationReason()));
        addAll(dto.getImmediateActions(), builder::addAllImmediateActions);
        addAll(dto.getTestsToOrder(), builder::addAllTestsToOrder);
        addAll(dto.getMonitoring(), builder::addAllMonitoring);
        addAll(dto.getRedFlags(), builder::addAllRedFlags);
        addAll(dto.getEstimatedFields(), builder::addAllEstimatedFields);
        return builder.build();
    }

    private static void addAll(java.util.List<String> values, java.util.function.Consumer<Iterable<String>> sink) {
        if (values != null) {
            sink.accept(values);
        }
    }

    public static RankedPatientDTO toDto(RankedPatient message) {
        RankedPatientDTO.RankedPatientDTOBuilder builder = RankedPatientDTO.builder()
                .patientId(uuid(message.getPatientId()))
                .name(emptyToNull(message.getName()))
                .document(emptyToNull(message.getDocument()))
                .age(message.getAge())
                .sex(emptyToNull(message.getSex()))
                .conditionId(emptyToNull(message.getConditionId()))
                .reason(emptyToNull(message.getReason()))
                .history(emptyToNull(message.getHistory()))
                .triageLevel(level(message.getTriageLevel()))
                .status(status(message.getStatus()))
                .evaluatedAt(time(message.getEvaluatedAt()))
                .scoreSource(emptyToNull(message.getScoreSource()))
                .aiActive(message.getAiActive())
                .dangerLead(emptyToNull(message.getDangerLead()))
                .aiMeaning(emptyToNull(message.getAiMeaning()))
                .aiReasons(new ArrayList<>(message.getAiReasonsList()))
                .datasetReasons(new ArrayList<>(message.getDatasetReasonsList()))
                .geminiReasons(new ArrayList<>(message.getGeminiReasonsList()))
                .consciousness(emptyToNull(message.getConsciousness()))
                .immediateActions(new ArrayList<>(message.getImmediateActionsList()))
                .testsToOrder(new ArrayList<>(message.getTestsToOrderList()))
                .monitoring(new ArrayList<>(message.getMonitoringList()))
                .redFlags(new ArrayList<>(message.getRedFlagsList()))
                .symptomExplanation(emptyToNull(message.getSymptomExplanation()))
                .photoFindings(emptyToNull(message.getPhotoFindings()))
                .destinationArea(emptyToNull(message.getDestinationArea()))
                .destinationCode(emptyToNull(message.getDestinationCode()))
                .destinationReason(emptyToNull(message.getDestinationReason()))
                .estimatedFields(new ArrayList<>(message.getEstimatedFieldsList()));

        if (message.hasGlucose()) {
            builder.glucose(message.getGlucose());
        }
        if (message.hasPainScale()) {
            builder.painScale(message.getPainScale());
        }
        if (message.hasMaxWaitMinutes()) {
            builder.maxWaitMinutes(message.getMaxWaitMinutes());
        }

        if (message.hasRiskScore()) {
            builder.riskScore(BigDecimal.valueOf(message.getRiskScore()).setScale(1, RoundingMode.HALF_UP));
        }
        if (message.hasQueuePosition()) {
            builder.queuePosition(message.getQueuePosition());
        }
        if (message.hasNews2()) {
            builder.news2(message.getNews2());
        }
        if (message.hasProtocolScore()) {
            builder.protocolScore(message.getProtocolScore());
        }
        if (message.hasGeminiScore()) {
            builder.geminiScore(message.getGeminiScore());
        }
        if (message.getHasVitals()) {
            builder.heartRate(message.getHeartRate())
                    .spo2(message.getSpo2())
                    .systolic(message.getSystolic())
                    .diastolic(message.getDiastolic())
                    .respiratoryRate(message.getRespiratoryRate())
                    .temperature(BigDecimal.valueOf(message.getTemperature()).setScale(1, RoundingMode.HALF_UP));
        }
        return builder.build();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static TriageLevel level(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TriageLevel.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static CareStatus status(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return CareStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static OffsetDateTime time(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
