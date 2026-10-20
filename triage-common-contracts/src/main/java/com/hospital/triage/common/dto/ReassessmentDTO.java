package com.hospital.triage.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReassessmentDTO {
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
