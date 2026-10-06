package com.hospital.triage.domain.entity;

import com.hospital.triage.domain.enums.EstadoAtencion;
import com.hospital.triage.domain.enums.NivelTriage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "evaluaciones_triage")
public class EvaluacionTriage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signos_id")
    private SignosVitales signos;

    @Column(name = "score_riesgo", nullable = false, precision = 5, scale = 1)
    private BigDecimal scoreRiesgo;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_triage", nullable = false, length = 32)
    private NivelTriage nivelTriage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EstadoAtencion estado = EstadoAtencion.EN_ESPERA;

    @Column(name = "posicion_cola")
    private Integer posicionCola;

    @Column(name = "news2")
    private Integer news2;

    @Column(name = "evaluado_en", nullable = false)
    private OffsetDateTime evaluadoEn = OffsetDateTime.now();
}
