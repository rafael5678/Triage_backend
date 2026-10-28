package com.hospital.triage.queue.api;

import com.hospital.triage.common.dto.BoardEntryDTO;
import com.hospital.triage.common.dto.QueueStateDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.TriageStatsDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.queue.application.service.InsightService;
import com.hospital.triage.queue.application.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/triage")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final InsightService insights;

    @GetMapping({"/health", "/salud"})
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "ms-cola-espera",
                "port", "8083"
        );
    }

    @GetMapping({"/queue", "/cola"})
    public QueueStateDTO queueState() {
        return queueService.queue();
    }

    @GetMapping({"/patients", "/pacientes"})
    public List<RankedPatientDTO> patients() {
        return queueService.all();
    }

    @GetMapping({"/stats", "/estadisticas"})
    public TriageStatsDTO stats() {
        return insights.stats();
    }

    /** Pantalla pública de sala: sin nombres ni documento completo. */
    @GetMapping({"/board", "/tablero"})
    public List<BoardEntryDTO> board() {
        return insights.board();
    }

    @PatchMapping({"/care/{patientId}", "/atender/{patientId}"})
    public RankedPatientDTO startCare(@PathVariable UUID patientId) {
        return queueService.startCare(patientId);
    }

    @PatchMapping({"/care/{patientId}/status", "/atender/{patientId}/estado"})
    public RankedPatientDTO updateStatus(@PathVariable UUID patientId,
                                         @RequestParam(value = "status") CareStatus status) {
        return queueService.updateStatus(patientId, status);
    }
}
