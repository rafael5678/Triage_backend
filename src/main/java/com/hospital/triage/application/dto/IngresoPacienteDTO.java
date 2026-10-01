package com.hospital.triage.application.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class IngresoPacienteDTO {
    @NotBlank
    @Size(max = 160)
    private String nombre;

    @NotBlank
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
}
