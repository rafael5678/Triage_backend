package com.hospital.triage.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "symptoms")
public class Symptom {

    @Id
    @Column(length = 60)
    private String id;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(length = 400)
    private String hint;

    @Column(nullable = false)
    private int severity;

    @Column(nullable = false)
    private boolean active = true;
}
