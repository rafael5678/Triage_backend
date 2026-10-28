package com.hospital.triage.queue.application.service;

import com.hospital.triage.common.dto.QueueStateDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.queue.application.port.EvaluationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    /** Primero el puntaje más alto; si empatan, quien llegó antes. */
    static final Comparator<RankedPatientDTO> PRIORITY = Comparator
            .comparing(RankedPatientDTO::getRiskScore, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(p -> p.getArrivedAt() != null ? p.getArrivedAt() : p.getEvaluatedAt(),
                    Comparator.nullsLast(Comparator.naturalOrder()));

    private final EvaluationPort evaluations;

    public QueueStateDTO queue() {
        List<RankedPatientDTO> list = ranked(CareStatus.EN_ESPERA);
        return QueueStateDTO.builder()
                .generatedAt(OffsetDateTime.now())
                .waitingCount(list.size())
                .queue(list)
                .build();
    }

    public List<RankedPatientDTO> all() {
        OffsetDateTime now = OffsetDateTime.now();
        List<RankedPatientDTO> rows = evaluations.listAll();
        rows.forEach(p -> {
            if (p.getStatus() == CareStatus.EN_ESPERA) {
                WaitClock.stamp(p, now);
            }
        });
        return rows;
    }

    public RankedPatientDTO startCare(UUID patientId) {
        return updateStatus(patientId, CareStatus.EN_ATENCION);
    }

    public RankedPatientDTO updateStatus(UUID patientId, CareStatus newStatus) {
        RankedPatientDTO updated = evaluations.updateStatus(patientId, newStatus);
        ranked(CareStatus.EN_ESPERA);
        return updated;
    }

    public RankedPatientDTO setPresence(UUID patientId, boolean present) {
        return evaluations.setPresence(patientId, present);
    }

    public RankedPatientDTO markNotified(UUID patientId, String channel) {
        return evaluations.markNotified(patientId, channel);
    }

    public List<RankedPatientDTO> ranked(CareStatus status) {
        List<RankedPatientDTO> latest = sorted(evaluations.listByStatus(status));
        evaluations.applyPositions(latest);
        return latest;
    }

    /** Ordena, numera y calcula tiempos sin escribir nada (para lecturas frecuentes). */
    static List<RankedPatientDTO> sorted(List<RankedPatientDTO> rows) {
        OffsetDateTime now = OffsetDateTime.now();
        List<RankedPatientDTO> latest = new ArrayList<>(rows);
        latest.sort(PRIORITY);
        int position = 1;
        for (RankedPatientDTO patient : latest) {
            patient.setQueuePosition(position++);
            WaitClock.stamp(patient, now);
        }
        return latest;
    }
}
