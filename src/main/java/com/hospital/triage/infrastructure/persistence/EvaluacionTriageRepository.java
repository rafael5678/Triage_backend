package com.hospital.triage.infrastructure.persistence;

import com.hospital.triage.domain.entity.EvaluacionTriage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EvaluacionTriageRepository extends JpaRepository<EvaluacionTriage, UUID> {
}
