package com.hospital.triage.queue.application.service;

import com.hospital.triage.common.dto.QueueStateDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;
import com.hospital.triage.queue.application.port.EvaluationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private EvaluationPort evaluations;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(evaluations);
    }

    @Test
    @DisplayName("QC-01: Ordenamiento prioritario de la cola por score de riesgo descendente")
    void qc01_ordenamientoPrioritarioPorRiesgo() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        RankedPatientDTO leve = RankedPatientDTO.builder()
                .patientId(id1)
                .name("Paciente Leve")
                .riskScore(BigDecimal.valueOf(30.0))
                .triageLevel(TriageLevel.TRIAGE_IV_VERDE)
                .status(CareStatus.EN_ESPERA)
                .evaluatedAt(OffsetDateTime.now().minusMinutes(10))
                .build();
        RankedPatientDTO critico = RankedPatientDTO.builder()
                .patientId(id2)
                .name("Paciente Critico")
                .riskScore(BigDecimal.valueOf(88.0))
                .triageLevel(TriageLevel.TRIAGE_I_ROJO)
                .status(CareStatus.EN_ESPERA)
                .evaluatedAt(OffsetDateTime.now().minusMinutes(5))
                .build();

        when(evaluations.listByStatus(CareStatus.EN_ESPERA)).thenReturn(List.of(leve, critico));

        QueueStateDTO state = queueService.queue();

        assertThat(state.getWaitingCount()).isEqualTo(2);
        List<RankedPatientDTO> queue = state.getQueue();
        assertThat(queue.get(0).getName()).isEqualTo("Paciente Critico");
        assertThat(queue.get(0).getQueuePosition()).isEqualTo(1);
        assertThat(queue.get(1).getName()).isEqualTo("Paciente Leve");
        assertThat(queue.get(1).getQueuePosition()).isEqualTo(2);
        verify(evaluations).applyPositions(queue);
    }

    @Test
    @DisplayName("QC-02: Transición de estado a EN_ATENCION al iniciar atención")
    void qc02_transicionEstadoEnAtencion() {
        UUID patientId = UUID.randomUUID();
        RankedPatientDTO updated = RankedPatientDTO.builder()
                .patientId(patientId)
                .name("Paciente Test")
                .riskScore(BigDecimal.valueOf(70.0))
                .triageLevel(TriageLevel.TRIAGE_II_NARANJA)
                .status(CareStatus.EN_ATENCION)
                .build();

        when(evaluations.updateStatus(patientId, CareStatus.EN_ATENCION)).thenReturn(updated);
        when(evaluations.listByStatus(CareStatus.EN_ESPERA)).thenReturn(List.of());

        RankedPatientDTO result = queueService.startCare(patientId);

        assertThat(result.getStatus()).isEqualTo(CareStatus.EN_ATENCION);
        verify(evaluations).updateStatus(patientId, CareStatus.EN_ATENCION);
        verify(evaluations).applyPositions(List.of());
    }
}
