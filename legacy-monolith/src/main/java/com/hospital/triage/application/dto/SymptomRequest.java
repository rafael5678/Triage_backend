package com.hospital.triage.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SymptomRequest {
    private String id;
    @NotBlank
    private String label;
    private String hint;
    @NotNull
    @Min(0)
    @Max(100)
    private Integer severity;
    private Boolean active = true;
}
