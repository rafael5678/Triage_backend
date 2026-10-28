package com.hospital.triage.queue.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.queue.application.port.EvaluationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Avisa por WhatsApp a quien espera cuando su turno se acerca o su tiempo máximo está por vencerse.
 * Si la persona se registró pero no ha llegado a la sala, el mensaje le pide presentarse.
 * Cada paciente recibe un solo aviso por visita (queda marcado en evaluación).
 * Sin WHATSAPP_TOKEN y WHATSAPP_PHONE_ID no envía nada: el personal usa el botón de la app.
 */
@Slf4j
@Component
public class WhatsAppNotifier {

    private static final Duration MIN_INTERVAL = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "whatsapp-notifier");
        t.setDaemon(true);
        return t;
    });
    private final ObjectMapper json;
    private final EvaluationPort evaluations;
    private Instant lastRun = Instant.EPOCH;

    @Value("${app.whatsapp.token:}")
    private String token;

    @Value("${app.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    /** Plantilla aprobada en Meta; sin ella se envía texto libre (solo llega si el paciente escribió en las últimas 24 h). */
    @Value("${app.whatsapp.template:}")
    private String template;

    @Value("${app.whatsapp.template-language:es}")
    private String templateLanguage;

    /** Avisar cuando falten estos minutos para el tiempo máximo… */
    @Value("${app.whatsapp.lead-minutes:10}")
    private int leadMinutes = 10;

    /** …o cuando quede entre los primeros de la fila. */
    @Value("${app.whatsapp.position-lead:2}")
    private int positionLead = 2;

    public WhatsAppNotifier(ObjectMapper json, EvaluationPort evaluations) {
        this.json = json;
        this.evaluations = evaluations;
    }

    public boolean automatic() {
        return token != null && !token.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank();
    }

    /** Lanza la revisión en segundo plano para no demorar la respuesta de la cola. */
    public void checkAsync(List<RankedPatientDTO> rankedWaiting) {
        if (!automatic()) {
            return;
        }
        synchronized (this) {
            Instant now = Instant.now();
            if (Duration.between(lastRun, now).compareTo(MIN_INTERVAL) < 0) {
                return;
            }
            lastRun = now;
        }
        List<RankedPatientDTO> snapshot = List.copyOf(rankedWaiting);
        worker.submit(() -> notifyDue(snapshot));
    }

    synchronized int notifyDue(List<RankedPatientDTO> rankedWaiting) {
        int sent = 0;
        for (RankedPatientDTO p : rankedWaiting) {
            if (!due(p, leadMinutes, positionLead)) {
                continue;
            }
            try {
                if (send(p)) {
                    evaluations.markNotified(p.getPatientId(), "WHATSAPP");
                    sent++;
                }
            } catch (Exception ex) {
                log.warn("No se pudo avisar por WhatsApp a {}: {}", p.getPatientId(), ex.getMessage());
            }
        }
        return sent;
    }

    static boolean due(RankedPatientDTO p, int leadMinutes, int positionLead) {
        if (p.getStatus() != CareStatus.EN_ESPERA || p.getPatientId() == null) {
            return false;
        }
        if (p.getPhone() == null || p.getPhone().isBlank() || p.getNotifiedAt() != null) {
            return false;
        }
        boolean timeAlmostUp = p.getMinutesLeft() != null && p.getMinutesLeft() <= leadMinutes;
        boolean nearTheFront = p.getQueuePosition() != null && p.getQueuePosition() <= positionLead;
        return timeAlmostUp || nearTheFront;
    }

    /** Texto del aviso; el frontend arma el mismo para el botón manual. */
    static String message(RankedPatientDTO p) {
        String name = firstName(p.getName());
        String area = p.getDestinationArea() == null ? "Urgencias" : p.getDestinationArea();
        int left = Math.max(0, p.getMinutesLeft() == null ? 10 : p.getMinutesLeft());
        if (Boolean.FALSE.equals(p.getPresent())) {
            return "Hola " + name + ", le escribimos de Urgencias. Su turno en " + area
                    + " está por vencerse y aún no registramos su llegada. Por favor preséntese en los próximos "
                    + Math.max(5, left) + " minutos para conservar su prioridad.";
        }
        return "Hola " + name + ", le escribimos de Urgencias. Su turno en " + area
                + " está cerca (puesto " + p.getQueuePosition() + "). Por favor permanezca en la sala de espera.";
    }

    private boolean send(RankedPatientDTO p) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("messaging_product", "whatsapp");
        body.put("to", p.getPhone());
        if (template != null && !template.isBlank()) {
            body.put("type", "template");
            ObjectNode tpl = body.putObject("template");
            tpl.put("name", template.trim());
            tpl.putObject("language").put("code", templateLanguage);
            var params = tpl.putArray("components").addObject().put("type", "body").putArray("parameters");
            params.addObject().put("type", "text").put("text", firstName(p.getName()));
            params.addObject().put("type", "text").put("text", p.getDestinationArea() == null ? "Urgencias" : p.getDestinationArea());
            params.addObject().put("type", "text").put("text", Boolean.FALSE.equals(p.getPresent())
                    ? "aún no registramos su llegada; preséntese pronto para conservar su prioridad"
                    : "su turno está cerca; permanezca en la sala de espera");
        } else {
            body.put("type", "text");
            body.putObject("text").put("body", message(p));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/v21.0/" + phoneNumberId.trim() + "/messages"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            log.warn("WhatsApp respondió {}: {}", response.statusCode(), response.body());
            return false;
        }
        return true;
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim().split("\\s+")[0];
    }
}
