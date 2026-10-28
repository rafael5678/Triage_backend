package com.hospital.triage.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageStatsDTO {
    private OffsetDateTime generatedAt;
    private int totalPatients;
    private int waiting;
    private int inCare;
    private int attended;
    private Double averageScore;
    private Double averageWaitingMinutes;
    private Integer longestWaitingMinutes;
    private Double aiActivePercent;
    /** Minutos reales entre el triaje y el inicio de la atención, de quienes ya fueron atendidos. */
    private Double averageTimeToCareMinutes;
    private int overdue;
    @Builder.Default
    private Map<String, Integer> byLevel = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Integer> byArea = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Integer> byStatus = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Integer> byCondition = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Integer> arrivalsByHour = new LinkedHashMap<>();
}
