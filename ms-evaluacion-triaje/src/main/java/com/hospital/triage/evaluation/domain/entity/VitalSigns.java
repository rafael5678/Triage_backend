package com.hospital.triage.evaluation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "signos_vitales")
public class VitalSigns {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID patientId;

    @Column(name = "frecuencia_cardiaca", nullable = false)
    private Integer heartRate;

    @Column(nullable = false)
    private Integer spo2;

    @Column(name = "pas", nullable = false)
    private Integer systolic;

    @Column(name = "pad", nullable = false)
    private Integer diastolic;

    @Column(name = "temperatura", nullable = false, precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(name = "frecuencia_respiratoria", nullable = false)
    private Integer respiratoryRate;

    @Column(name = "registrado_en", nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    @Column(name = "glucosa")
    private Integer glucose;

    @Column(name = "dolor")
    private Integer painScale;

    /** Escala AVPU: A, C, V, P o U. */
    @Column(name = "conciencia", length = 1)
    private String consciousness;
}
