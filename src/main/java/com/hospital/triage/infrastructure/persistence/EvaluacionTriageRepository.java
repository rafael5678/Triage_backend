package com.hospital.triage.infrastructure.persistence;

import com.hospital.triage.domain.entity.EvaluacionTriage;
import com.hospital.triage.domain.enums.EstadoAtencion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluacionTriageRepository extends JpaRepository<EvaluacionTriage, UUID> {

    @EntityGraph(attributePaths = {"paciente", "signos"})
    List<EvaluacionTriage> findByEstadoOrderByScoreRiesgoDescEvaluadoEnAsc(EstadoAtencion estado);

    @EntityGraph(attributePaths = {"paciente", "signos"})
    Optional<EvaluacionTriage> findTopByPaciente_IdOrderByEvaluadoEnDesc(UUID pacienteId);

    @EntityGraph(attributePaths = {"paciente", "signos"})
    List<EvaluacionTriage> findAllByOrderByEvaluadoEnDesc();
}
