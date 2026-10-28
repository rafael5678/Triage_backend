package com.hospital.triage.evaluation.infrastructure.persistence;

import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.evaluation.domain.entity.TriageEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TriageEvaluationRepository extends JpaRepository<TriageEvaluation, UUID> {
    Optional<TriageEvaluation> findTopByPatientIdAndCurrentTrueOrderByEvaluatedAtDesc(UUID patientId);

    List<TriageEvaluation> findByStatusAndCurrentTrueOrderByRiskScoreDescEvaluatedAtAsc(CareStatus status);

    List<TriageEvaluation> findByCurrentTrueOrderByEvaluatedAtDesc();

    List<TriageEvaluation> findByPatientIdOrderByEvaluatedAtAsc(UUID patientId);

    long countByCurrentFalse();
}
