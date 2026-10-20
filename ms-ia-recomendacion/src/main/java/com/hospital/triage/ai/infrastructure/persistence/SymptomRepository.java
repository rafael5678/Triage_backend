package com.hospital.triage.ai.infrastructure.persistence;

import com.hospital.triage.ai.domain.entity.Symptom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SymptomRepository extends JpaRepository<Symptom, String> {
    List<Symptom> findByActiveTrueOrderBySeverityDesc();
    List<Symptom> findAllByOrderBySeverityDesc();
}
