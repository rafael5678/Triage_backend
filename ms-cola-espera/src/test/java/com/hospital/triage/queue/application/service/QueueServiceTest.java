package com.hospital.triage.queue.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.common.dto.QueueStateDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;
import com.hospital.triage.queue.domain.entity.Patient;
import com.hospital.triage.queue.domain.entity.TriageEvaluation;
import com.hospital.triage.queue.infrastructure.persistence.PatientRepository;
import com.hospital.triage.queue.infrastructure.persistence.TriageEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private PatientRepository patients;

    @Mock
    private TriageEvaluationRepository evaluations;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(patients, evaluations, new ObjectMapper());
    }

    @Test
    @DisplayName("QC-01: Ordenamiento prioritario de la cola por score de riesgo descendente")
    void qc01_ordenamientoPrioritarioPorRiesgo() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Patient p1 = new Patient();
        p1.setId(id1);
        p1.setName("Paciente Leve");

        Patient p2 = new Patient();
        p2.setId(id2);
        p2.setName("Paciente Critico");

        TriageEvaluation ev1 = new TriageEvaluation();
        ev1.setId(UUID.randomUUID());
        ev1.setPatient(p1);
        ev1.setRiskScore(BigDecimal.valueOf(30.0));
        ev1.setTriageLevel(TriageLevel.TRIAGE_IV_VERDE);
        ev1.setStatus(CareStatus.EN_ESPERA);
        ev1.setEvaluatedAt(OffsetDateTime.now().minusMinutes(10));

        TriageEvaluation ev2 = new TriageEvaluation();
        ev2.setId(UUID.randomUUID());
        ev2.setPatient(p2);
        ev2.setRiskScore(BigDecimal.valueOf(88.0));
        ev2.setTriageLevel(TriageLevel.TRIAGE_I_ROJO);
        ev2.setStatus(CareStatus.EN_ESPERA);
        ev2.setEvaluatedAt(OffsetDateTime.now().minusMinutes(5));

        when(evaluations.findByStatusOrderByRiskScoreDescEvaluatedAtAsc(CareStatus.EN_ESPERA))
                .thenReturn(List.of(ev2, ev1));

        QueueStateDTO state = queueService.queue();

        assertThat(state.getWaitingCount()).isEqualTo(2);
        List<RankedPatientDTO> queue = state.getQueue();
        assertThat(queue.get(0).getName()).isEqualTo("Paciente Critico");
        assertThat(queue.get(0).getQueuePosition()).isEqualTo(1);
        assertThat(queue.get(1).getName()).isEqualTo("Paciente Leve");
        assertThat(queue.get(1).getQueuePosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("QC-02: Transición de estado a EN_ATENCION al iniciar atención")
    void qc02_transicionEstadoEnAtencion() {
        UUID patientId = UUID.randomUUID();

        Patient p = new Patient();
        p.setId(patientId);
        p.setName("Paciente Test");

        TriageEvaluation ev = new TriageEvaluation();
        ev.setId(UUID.randomUUID());
        ev.setPatient(p);
        ev.setRiskScore(BigDecimal.valueOf(70.0));
        ev.setTriageLevel(TriageLevel.TRIAGE_II_NARANJA);
        ev.setStatus(CareStatus.EN_ESPERA);
        ev.setQueuePosition(1);

        when(patients.findById(patientId)).thenReturn(Optional.of(p));
        when(evaluations.findTopByPatient_IdOrderByEvaluatedAtDesc(patientId)).thenReturn(Optional.of(ev));
        when(evaluations.findByStatusOrderByRiskScoreDescEvaluatedAtAsc(CareStatus.EN_ESPERA))
                .thenReturn(List.of());

        RankedPatientDTO result = queueService.startCare(patientId);

        assertThat(result.getStatus()).isEqualTo(CareStatus.EN_ATENCION);
        assertThat(ev.getStatus()).isEqualTo(CareStatus.EN_ATENCION);
        assertThat(ev.getQueuePosition()).isNull();
        verify(evaluations).save(ev);
    }
}
