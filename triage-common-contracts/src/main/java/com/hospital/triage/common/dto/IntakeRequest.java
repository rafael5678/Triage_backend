package com.hospital.triage.common.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Relato libre del ingreso; la IA lo convierte en el formulario de triaje. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntakeRequest {
    @Size(max = 4000)
    private String story;

    @Size(max = 4_000_000)
    private String photoBase64;

    @Size(max = 40)
    private String photoMime;

    /** Lo que el personal ya escribió en el formulario; la IA no lo reemplaza. */
    private Map<String, Object> known;

    /** Antecedentes que muestra el formulario (id y texto), para que la IA marque los mismos. */
    @Builder.Default
    private List<Option> comorbidityOptions = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private String id;
        private String label;
    }
}
