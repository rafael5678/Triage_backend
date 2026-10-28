package com.hospital.triage.evaluation.domain.entity;

import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

    @Column(name = "paciente_id", nullable = false)
    private UUID patientId;

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

    @Column(name = "explicacion_ia", columnDefinition = "TEXT")
    private String aiExplanation;

    @Column(name = "area_destino", length = 40)
    private String destinationArea;

    @Column(name = "codigo_destino", length = 60)
    private String destinationCode;

    @Column(name = "motivo_destino", length = 300)
    private String destinationReason;

    @Column(name = "espera_max_min")
    private Integer maxWaitMinutes;

    /** Signos que estimó la IA en lugar de medirse, separados por coma. */
    @Column(name = "campos_estimados", length = 200)
    private String estimatedFields;
}
