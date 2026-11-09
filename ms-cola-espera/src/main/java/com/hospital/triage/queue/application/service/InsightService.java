package com.hospital.triage.queue.application.service;

import com.hospital.triage.common.TurnTicket;
import com.hospital.triage.common.TriageStatsCalculator;
import com.hospital.triage.common.dto.BoardEntryDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.TriageStatsDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.queue.application.port.EvaluationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Waiting-room stats and public board, computed from evaluations delivered over gRPC. */
@Service
@RequiredArgsConstructor
public class InsightService {

    private static final int MINUTES_PER_PATIENT_AHEAD = 8;

    private final EvaluationPort evaluations;

    public TriageStatsDTO stats() {
        return stats(evaluations.listAll(), OffsetDateTime.now());
    }

    TriageStatsDTO stats(List<RankedPatientDTO> all, OffsetDateTime now) {
        return TriageStatsCalculator.from(all, now);
    }

    /** Read-only: sort in memory and do not rewrite queue positions. */
    public List<BoardEntryDTO> board() {
        List<RankedPatientDTO> waiting = evaluations.listByStatus(CareStatus.EN_ESPERA);
        List<RankedPatientDTO> inCare = evaluations.listByStatus(CareStatus.EN_ATENCION);
        return board(waiting, inCare, OffsetDateTime.now());
    }

    List<BoardEntryDTO> board(List<RankedPatientDTO> waitingRaw, List<RankedPatientDTO> inCare, OffsetDateTime now) {
        List<RankedPatientDTO> waiting = new ArrayList<>(waitingRaw);
        waiting.sort(QueueService.PRIORITY);
        List<BoardEntryDTO> out = new ArrayList<>();
        for (int i = 0; i < waiting.size(); i++) {
            RankedPatientDTO p = waiting.get(i);
            int waited = WaitClock.waited(p, now);
            int target = WaitClock.maxWait(p);
            int estimated = Math.max(0, target + i * MINUTES_PER_PATIENT_AHEAD - waited);
            out.add(entry(p, i + 1, waited, estimated, target - waited < 0));
        }
        inCare.stream()
                .sorted(Comparator.comparing(RankedPatientDTO::getEvaluatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(p -> out.add(entry(p, null, WaitClock.waited(p, now), 0, false)));
        return out;
    }

    private BoardEntryDTO entry(RankedPatientDTO p, Integer position, int waited, int estimated, boolean overdue) {
        return BoardEntryDTO.builder()
                .overdue(overdue)
                .present(!Boolean.FALSE.equals(p.getPresent()))
                .ticket(ticket(p.getName(), p.getDocument()))
                .position(position)
                .triageLevel(p.getTriageLevel())
                .status(p.getStatus())
                .destinationArea(p.getDestinationArea())
                .destinationCode(p.getDestinationCode())
                .maxWaitMinutes(p.getMaxWaitMinutes())
                .waitedMinutes(waited)
                .estimatedWaitMinutes(estimated)
                .arrivedAt(p.getArrivedAt() != null ? p.getArrivedAt() : p.getEvaluatedAt())
                .build();
    }

    /** Ticket that does not identify the person: initials and the last 3 characters of the document. */
    static String ticket(String name, String document) {
        return TurnTicket.of(name, document);
    }
}
