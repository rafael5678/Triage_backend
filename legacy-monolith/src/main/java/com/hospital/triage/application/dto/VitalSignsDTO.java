package com.hospital.triage.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class VitalSignsDTO {
    @NotNull
    @JsonAlias("pacienteId")
    private UUID patientId;

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
}
