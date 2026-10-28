package com.hospital.triage.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankedPatientDTO {
    @JsonAlias("pacienteId")
    private UUID patientId;
    @JsonAlias("nombre")
    private String name;
    @JsonAlias("documento")
    private String document;
    @JsonAlias("edad")
    private Integer age;
    @JsonAlias("sexo")
    private String sex;
    @JsonAlias("lesionId")
    private String conditionId;
    @JsonAlias("motivo")
    private String reason;
    @JsonAlias("antecedentes")
    private String history;
    @JsonAlias("scoreRiesgo")
    private BigDecimal riskScore;
    @JsonAlias("nivelTriage")
    private TriageLevel triageLevel;
    @JsonAlias("estado")
    private CareStatus status;
    @JsonAlias("posicionCola")
    private Integer queuePosition;
    private Integer news2;
    @JsonAlias("evaluadoEn")
    private OffsetDateTime evaluatedAt;
    @JsonAlias("frecuenciaCardiaca")
    private Integer heartRate;
    private Integer spo2;
    @JsonAlias("presionSistolica")
    private Integer systolic;
    @JsonAlias("presionDiastolica")
    private Integer diastolic;
    @JsonAlias("temperatura")
    private BigDecimal temperature;
    @JsonAlias("frecuenciaRespiratoria")
    private Integer respiratoryRate;
    @JsonAlias("fuenteScore")
    private String scoreSource;
    @JsonAlias("iaActiva")
    private Boolean aiActive;
    private String dangerLead;
    @JsonAlias("meaningIa")
    private String aiMeaning;
    @JsonAlias("scoreProtocolo")
    private Double protocolScore;
    @JsonAlias("scoreGemini")
    private Double geminiScore;
    @Builder.Default
    @JsonAlias("razonesIa")
    private List<String> aiReasons = new ArrayList<>();
    @Builder.Default
    @JsonAlias("razonesDataset")
    private List<String> datasetReasons = new ArrayList<>();
    @Builder.Default
    @JsonAlias("razonesGemini")
    private List<String> geminiReasons = new ArrayList<>();
    @JsonAlias("glucosa")
    private Integer glucose;
    @JsonAlias("dolor")
    private Integer painScale;
    @JsonAlias("conciencia")
    private String consciousness;
    @Builder.Default
    private List<String> immediateActions = new ArrayList<>();
    @Builder.Default
    private List<String> testsToOrder = new ArrayList<>();
    @Builder.Default
    private List<String> monitoring = new ArrayList<>();
    @Builder.Default
    private List<String> redFlags = new ArrayList<>();
    private String symptomExplanation;
    private String photoFindings;
    private String destinationArea;
    private String destinationCode;
    private String destinationReason;
    private Integer maxWaitMinutes;
    @Builder.Default
    private List<String> estimatedFields = new ArrayList<>();
}
