package com.hospital.triage.queue.api;

import com.hospital.triage.common.dto.BoardEntryDTO;
import com.hospital.triage.common.dto.QueueStateDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.TriageStatsDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.queue.application.service.InsightService;
import com.hospital.triage.queue.application.service.QueueEvents;
import com.hospital.triage.queue.application.service.QueueService;
import com.hospital.triage.queue.application.service.WhatsAppNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/triage")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final InsightService insights;
    private final QueueEvents events;
    private final WhatsAppNotifier whatsapp;

    @GetMapping({"/health", "/salud"})
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "ms-cola-espera",
                "port", "8083",
                "whatsapp", whatsapp.automatic() ? "automatico" : "manual",
                "listeners", events.listeners()
        );
    }

    @GetMapping({"/queue", "/cola"})
    public QueueStateDTO queueState() {
        QueueStateDTO state = queueService.queue();
        whatsapp.checkAsync(state.getQueue());
        return state;
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

    /** Eventos en vivo: cada pantalla recibe "cambio" y vuelve a pedir sus datos. */
    @GetMapping(value = {"/events", "/eventos"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return events.subscribe();
    }

    @PatchMapping({"/care/{patientId}", "/atender/{patientId}"})
    public RankedPatientDTO startCare(@PathVariable UUID patientId) {
        return changed(queueService.startCare(patientId));
    }

    @PatchMapping({"/care/{patientId}/status", "/atender/{patientId}/estado"})
    public RankedPatientDTO updateStatus(@PathVariable UUID patientId,
                                         @RequestParam(value = "status") CareStatus status) {
        return changed(queueService.updateStatus(patientId, status));
    }

    /** present=false: se registró pero todavía no está en la sala. */
    @PatchMapping({"/care/{patientId}/presence", "/atender/{patientId}/presencia"})
    public RankedPatientDTO presence(@PathVariable UUID patientId, @RequestParam("present") boolean present) {
        return changed(queueService.setPresence(patientId, present));
    }

    /** El personal avisó al paciente (por ejemplo, con el botón de WhatsApp de la app). */
    @PostMapping({"/care/{patientId}/notified", "/atender/{patientId}/avisado"})
    public RankedPatientDTO notified(@PathVariable UUID patientId,
                                     @RequestParam(value = "channel", defaultValue = "MANUAL") String channel) {
        return changed(queueService.markNotified(patientId, channel));
    }

    private RankedPatientDTO changed(RankedPatientDTO dto) {
        events.changed();
        return dto;
    }
}
