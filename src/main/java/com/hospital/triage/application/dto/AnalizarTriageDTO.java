package com.hospital.triage.application.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AnalizarTriageDTO {
    @NotBlank
    @Size(max = 160)
    private String nombre;

    @Size(max = 40)
    private String documento;

    @NotNull
    @Min(0)
    @Max(120)
    private Integer edad;

    @Size(max = 20)
    private String sexo;

    @Size(max = 40)
    private String lesionId;

    @Size(max = 500)
    private String motivo;

    @Size(max = 400)
    private String antecedentes;

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

    /** Score of the in-browser boosting model trained on the hospital dataset. */
    private Double scoreModeloLocal;
}
