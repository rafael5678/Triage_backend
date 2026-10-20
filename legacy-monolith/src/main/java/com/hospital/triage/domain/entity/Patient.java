package com.hospital.triage.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pacientes")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 160)
    private String name;

    @Column(name = "documento", nullable = false, unique = true, length = 40)
    private String document;

    @Column(name = "edad", nullable = false)
    private Integer age;

    @Column(name = "sexo", length = 20)
    private String sex;

    @Column(name = "lesion_id", length = 40)
    private String conditionId;

    @Column(name = "motivo", length = 500)
    private String reason;

    @Column(name = "antecedentes", length = 400)
    private String history;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VitalSigns> vitalSigns = new ArrayList<>();

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TriageEvaluation> evaluations = new ArrayList<>();
}
