package com.hospital.triage.evaluation.infrastructure.persistence;

import com.hospital.triage.evaluation.domain.entity.Symptom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SymptomRepository extends JpaRepository<Symptom, String> {
    List<Symptom> findByActiveTrueOrderBySeverityDesc();
    List<Symptom> findAllByOrderBySeverityDesc();
}
