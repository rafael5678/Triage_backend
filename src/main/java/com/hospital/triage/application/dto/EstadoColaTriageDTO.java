package com.hospital.triage.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoColaTriageDTO {
    private OffsetDateTime generadoEn;
    private int totalEnEspera;
    private List<PacientePriorizadoDTO> cola;
}
