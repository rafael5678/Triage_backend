package com.hospital.triage.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AnalyzeTriageDTO {
    @NotBlank
    @Size(max = 160)
    @JsonAlias("nombre")
    private String name;

    @Size(max = 40)
    @JsonAlias("documento")
    private String document;

    @NotNull
    @Min(0)
    @Max(120)
    @JsonAlias("edad")
    private Integer age;

    @Size(max = 20)
    @JsonAlias("sexo")
    private String sex;

    @Size(max = 40)
    @JsonAlias("lesionId")
    private String conditionId;

    @Size(max = 500)
    @JsonAlias("motivo")
    private String reason;

    @Size(max = 400)
    @JsonAlias("antecedentes")
    private String history;

    @NotNull @Min(20) @Max(250)
    @JsonAlias("frecuenciaCardiaca")
    private Integer heartRate;

    @NotNull @Min(0) @Max(100)
    private Integer spo2;

    @NotNull @Min(50) @Max(260)
    @JsonAlias("presionSistolica")
    private Integer systolic;

    @NotNull @Min(20) @Max(160)
    @JsonAlias("presionDiastolica")
    private Integer diastolic;

    @NotNull @DecimalMin("30.0") @DecimalMax("43.0")
    @JsonAlias("temperatura")
    private BigDecimal temperature;

    @NotNull @Min(4) @Max(60)
    @JsonAlias("frecuenciaRespiratoria")
    private Integer respiratoryRate;

    @JsonAlias("scoreModeloLocal")
    private Double localModelScore;
}
