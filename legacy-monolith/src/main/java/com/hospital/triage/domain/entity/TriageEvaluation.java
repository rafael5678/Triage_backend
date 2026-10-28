package com.hospital.triage.domain.entity;

import com.hospital.triage.domain.enums.CareStatus;
import com.hospital.triage.domain.enums.TriageLevel;
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
public class TriageEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signos_id")
    private VitalSigns vitalSigns;

    @Column(name = "score_riesgo", nullable = false, precision = 5, scale = 1)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_triage", nullable = false, length = 32)
    private TriageLevel triageLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 24)
    private CareStatus status = CareStatus.EN_ESPERA;

    @Column(name = "posicion_cola")
    private Integer queuePosition;

    @Column(name = "news2")
    private Integer news2;

    @Column(name = "evaluado_en", nullable = false)
    private OffsetDateTime evaluatedAt = OffsetDateTime.now();

    @Column(name = "fuente_score", length = 24)
    private String scoreSource;

    @Column(name = "explicacion_ia", length = 4000)
    private String aiExplanation;
}
