package com.hospital.triage.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomRequest {
    private String id;
    @NotBlank
    private String label;
    private String hint;
    @NotNull
    @Min(0)
    @Max(100)
    private Integer severity;
    @Builder.Default
    private Boolean active = true;
}
