package com.hospital.triage.ai.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Llamadas a Gemini hechas por día (zona America/Bogota). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "uso_gemini")
public class GeminiUsage {

    @Id
    @Column(name = "dia")
    private LocalDate day;

    @Column(name = "llamadas", nullable = false)
    private int calls;
}
