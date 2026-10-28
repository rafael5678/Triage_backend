package com.hospital.triage.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeTriageDTO implements ExtraFindings {
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

    @Min(10) @Max(1500)
    @JsonAlias("glucosa")
    private Integer glucose;

    @Min(0) @Max(10)
    @JsonAlias("dolor")
    private Integer painScale;

    @Pattern(regexp = "^[ACVPUacvpu]?$")
    @JsonAlias("conciencia")
    private String consciousness;

    @Size(max = 4_000_000)
    private String photoBase64;

    @Size(max = 40)
    private String photoMime;

    @Builder.Default
    private List<String> estimatedFields = new ArrayList<>();
}
