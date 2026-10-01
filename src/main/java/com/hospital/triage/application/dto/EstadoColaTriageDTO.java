package com.hospital.triage.application.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class EstadoColaTriageDTO {
    private OffsetDateTime generadoEn;
    private int totalEnEspera;
    private List<PacientePriorizadoDTO> cola;
}
