package com.hospital.triage.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientIntakeDTO {
    @NotBlank
    @Size(max = 160)
    @JsonAlias("nombre")
    private String name;

    @NotBlank
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
}
