package com.hospital.triage.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Formulario propuesto por la IA. estimatedFields lista los signos que no se midieron. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntakeSuggestion {
    private boolean active;
    private String conditionId;
    private String reason;
    @Builder.Default
    private List<String> comorbidityIds = new ArrayList<>();
    private Integer age;
    private String sex;
    private Integer heartRate;
    private Integer spo2;
    private Integer systolic;
    private Integer diastolic;
    private BigDecimal temperature;
    private Integer respiratoryRate;
    private Integer glucose;
    private Integer painScale;
    private String consciousness;
    @Builder.Default
    private List<String> estimatedFields = new ArrayList<>();
    private String photoFindings;
    @Builder.Default
    private List<String> notes = new ArrayList<>();
}
