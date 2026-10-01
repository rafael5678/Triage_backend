package com.hospital.triage.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "signos_vitales")
public class SignosVitales {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @Column(name = "frecuencia_cardiaca", nullable = false)
    private Integer frecuenciaCardiaca;

    @Column(nullable = false)
    private Integer spo2;

    @Column(name = "pas", nullable = false)
    private Integer presionSistolica;

    @Column(name = "pad", nullable = false)
    private Integer presionDiastolica;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal temperatura;

    @Column(name = "frecuencia_respiratoria", nullable = false)
    private Integer frecuenciaRespiratoria;

    @Column(name = "registrado_en", nullable = false)
    private OffsetDateTime registradoEn = OffsetDateTime.now();
}
