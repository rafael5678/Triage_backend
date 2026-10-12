package com.hospital.triage.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsistenciaIaDTO {
    private boolean activa;
    private Double scoreSugerido;
    private String dangerLead;
    private String meaning;
    @Builder.Default
    private List<String> reasons = new ArrayList<>();
    @Builder.Default
    private List<String> razonesDataset = new ArrayList<>();
    @Builder.Default
    private List<String> razonesGemini = new ArrayList<>();
    private Double scoreProtocolo;
    private Double scoreModeloLocal;
}
