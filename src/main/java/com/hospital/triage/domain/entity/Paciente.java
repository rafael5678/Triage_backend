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
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(nullable = false, unique = true, length = 40)
    private String documento;

    @Column(nullable = false)
    private Integer edad;

    @Column(length = 20)
    private String sexo;

    @Column(name = "lesion_id", length = 40)
    private String lesionId;

    @Column(length = 500)
    private String motivo;

    @Column(length = 400)
    private String antecedentes;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @OneToMany(mappedBy = "paciente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SignosVitales> signos = new ArrayList<>();

    @OneToMany(mappedBy = "paciente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EvaluacionTriage> evaluaciones = new ArrayList<>();
}
