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
        return evaluations.listAll();
    }

    public RankedPatientDTO startCare(UUID patientId) {
        return updateStatus(patientId, CareStatus.EN_ATENCION);
    }

    public RankedPatientDTO updateStatus(UUID patientId, CareStatus newStatus) {
        RankedPatientDTO updated = evaluations.updateStatus(patientId, newStatus);
        ranked(CareStatus.EN_ESPERA);
        return updated;
    }

    public List<RankedPatientDTO> ranked(CareStatus status) {
        List<RankedPatientDTO> latest = new ArrayList<>(evaluations.listByStatus(status));
        latest.sort(Comparator
                .comparing(RankedPatientDTO::getRiskScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RankedPatientDTO::getEvaluatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        int position = 1;
        for (RankedPatientDTO patient : latest) {
            patient.setQueuePosition(position++);
        }
        evaluations.applyPositions(latest);
        return latest;
    }
}
