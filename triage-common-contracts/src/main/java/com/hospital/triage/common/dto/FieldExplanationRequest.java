package com.hospital.triage.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldExplanationRequest {
    @NotBlank
    @Size(max = 120)
    private String field;

    @Size(max = 60)
    private String value;

    @Size(max = 600)
    private String context;
}
