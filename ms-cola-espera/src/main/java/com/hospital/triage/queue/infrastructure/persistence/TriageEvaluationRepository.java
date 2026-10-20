package com.hospital.triage.queue.infrastructure.persistence;

import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.queue.domain.entity.TriageEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TriageEvaluationRepository extends JpaRepository<TriageEvaluation, UUID> {
    Optional<TriageEvaluation> findTopByPatient_IdOrderByEvaluatedAtDesc(UUID patientId);
    List<TriageEvaluation> findByStatusOrderByRiskScoreDescEvaluatedAtAsc(CareStatus status);
    List<TriageEvaluation> findAllByOrderByEvaluatedAtDesc();
}
