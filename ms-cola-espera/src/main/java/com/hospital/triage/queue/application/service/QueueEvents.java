package com.hospital.triage.queue.application.service;

import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.queue.application.port.EvaluationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Eventos en vivo (SSE) para la sala del personal y la pantalla pública.
 * Mientras haya alguien conectado revisa la cola cada 5 segundos; si cambió, avisa con un evento
 * "cambio" y cada pantalla vuelve a pedir sus datos. El evento no lleva datos de pacientes.
 * Esa misma revisión dispara los avisos de WhatsApp, así funcionan mientras la sala esté abierta
 * aunque el servicio se duerma cuando nadie lo usa.
 */
@Slf4j
@Component
public class QueueEvents {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final EvaluationPort evaluations;
    private final WhatsAppNotifier notifier;
    private volatile String lastDigest = "";

    public QueueEvents(EvaluationPort evaluations, WhatsAppNotifier notifier) {
        this.evaluations = evaluations;
        this.notifier = notifier;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("hola").data("conectado").reconnectTime(5000));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public int listeners() {
        return emitters.size();
    }

    /** Llamado tras un cambio hecho por la propia cola (pasar a atención, presencia, aviso). */
    public void changed() {
        lastDigest = "";
        broadcast("cambio");
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void tick() {
        if (emitters.isEmpty()) {
            return;
        }
        try {
            List<RankedPatientDTO> waiting = QueueService.sorted(evaluations.listByStatus(CareStatus.EN_ESPERA));
            List<RankedPatientDTO> inCare = evaluations.listByStatus(CareStatus.EN_ATENCION);
            notifier.checkAsync(waiting);
            String digest = digest(waiting, inCare);
            if (!digest.equals(lastDigest)) {
                lastDigest = digest;
                broadcast("cambio");
            }
        } catch (Exception ex) {
            log.warn("No se pudo revisar la cola para los eventos: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedRate = 25000, initialDelay = 25000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("latido"));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    static String digest(List<RankedPatientDTO> waiting, List<RankedPatientDTO> inCare) {
        String w = waiting.stream()
                .map(p -> p.getPatientId() + ":" + p.getRiskScore() + ":" + p.getEvaluatedAt() + ":" + p.getPresent()
                        + ":" + p.getNotifiedAt() + ":" + p.getOverdue())
                .collect(Collectors.joining("|"));
        String c = inCare.stream().map(p -> String.valueOf(p.getPatientId())).collect(Collectors.joining("|"));
        return w + "#" + c;
    }

    private void broadcast(String name) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(name).data(String.valueOf(System.currentTimeMillis())));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
