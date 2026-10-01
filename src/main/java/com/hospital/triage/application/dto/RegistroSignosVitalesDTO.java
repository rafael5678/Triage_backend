package com.hospital.triage.application.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RegistroSignosVitalesDTO {
    @NotNull
    private UUID pacienteId;

    @NotNull @Min(20) @Max(250)
    private Integer frecuenciaCardiaca;

    @NotNull @Min(0) @Max(100)
    private Integer spo2;

    @NotNull @Min(50) @Max(260)
    private Integer presionSistolica;

    @NotNull @Min(20) @Max(160)
    private Integer presionDiastolica;

    @NotNull @DecimalMin("30.0") @DecimalMax("43.0")
    private BigDecimal temperatura;

    @NotNull @Min(4) @Max(60)
    private Integer frecuenciaRespiratoria;
}
