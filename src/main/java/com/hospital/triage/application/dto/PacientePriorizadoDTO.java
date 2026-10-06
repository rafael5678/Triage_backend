package com.hospital.triage.application.dto;

import com.hospital.triage.domain.enums.EstadoAtencion;
import com.hospital.triage.domain.enums.NivelTriage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacientePriorizadoDTO {
    private UUID pacienteId;
    private String nombre;
    private String documento;
    private Integer edad;
    private String sexo;
    private String lesionId;
    private String motivo;
    private String antecedentes;
    private BigDecimal scoreRiesgo;
    private NivelTriage nivelTriage;
    private EstadoAtencion estado;
    private Integer posicionCola;
    private Integer news2;
    private OffsetDateTime evaluadoEn;
    private Integer frecuenciaCardiaca;
    private Integer spo2;
    private Integer presionSistolica;
    private Integer presionDiastolica;
    private BigDecimal temperatura;
    private Integer frecuenciaRespiratoria;
}
