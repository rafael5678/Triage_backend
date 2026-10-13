package com.hospital.triage.infrastructure.persistence;

import com.hospital.triage.domain.entity.TriageEvaluation;
import com.hospital.triage.domain.enums.CareStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TriageEvaluationRepository extends JpaRepository<TriageEvaluation, UUID> {

    @EntityGraph(attributePaths = {"patient", "vitalSigns"})
    List<TriageEvaluation> findByStatusOrderByRiskScoreDescEvaluatedAtAsc(CareStatus status);

    @EntityGraph(attributePaths = {"patient", "vitalSigns"})
    Optional<TriageEvaluation> findTopByPatient_IdOrderByEvaluatedAtDesc(UUID patientId);

    @EntityGraph(attributePaths = {"patient", "vitalSigns"})
    List<TriageEvaluation> findAllByOrderByEvaluatedAtDesc();
}
