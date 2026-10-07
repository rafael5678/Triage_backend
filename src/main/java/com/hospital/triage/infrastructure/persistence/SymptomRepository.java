package com.hospital.triage.infrastructure.persistence;

import com.hospital.triage.domain.entity.Symptom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SymptomRepository extends JpaRepository<Symptom, String> {
    List<Symptom> findByActiveTrueOrderBySeverityDesc();
    List<Symptom> findAllByOrderBySeverityDesc();
}
