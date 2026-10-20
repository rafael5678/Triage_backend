package com.hospital.triage.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class AiAssistDTO {
    @JsonAlias("activa")
    private boolean active;
    @JsonAlias("scoreSugerido")
    private Double suggestedScore;
    private String dangerLead;
    private String meaning;
    @Builder.Default
    private List<String> reasons = new ArrayList<>();
    @Builder.Default
    @JsonAlias("razonesDataset")
    private List<String> datasetReasons = new ArrayList<>();
    @Builder.Default
    @JsonAlias("razonesGemini")
    private List<String> geminiReasons = new ArrayList<>();
    @JsonAlias("scoreProtocolo")
    private Double protocolScore;
    @JsonAlias("scoreModeloLocal")
    private Double localModelScore;
}
