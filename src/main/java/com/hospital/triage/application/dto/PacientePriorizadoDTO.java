package com.hospital.triage.application.dto;

import com.hospital.triage.domain.enums.EstadoAtencion;
import com.hospital.triage.domain.enums.NivelTriage;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class PacientePriorizadoDTO {
    private UUID pacienteId;
    private String nombre;
    private String documento;
    private Integer edad;
    private String lesionId;
    private BigDecimal scoreRiesgo;
    private NivelTriage nivelTriage;
    private EstadoAtencion estado;
    private Integer posicionCola;
    private Integer news2;
    private OffsetDateTime evaluadoEn;
}
