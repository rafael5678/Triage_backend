package com.hospital.triage.queue.application.service;

import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Reloj de espera, orden de la fila y cuándo se avisa por WhatsApp. */
class WaitAndNoticeTest {

    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-28T10:00:00-05:00");

    private RankedPatientDTO waiting(String name, double score, int arrivedMinutesAgo, int maxWait) {
        return RankedPatientDTO.builder()
                .patientId(UUID.randomUUID())
                .name(name)
                .document("1087782478")
                .age(40)
                .riskScore(BigDecimal.valueOf(score))
                .triageLevel(TriageLevel.TRIAGE_III_AMARILLO)
                .status(CareStatus.EN_ESPERA)
                .arrivedAt(now.minusMinutes(arrivedMinutesAgo))
                .evaluatedAt(now.minusMinutes(2))
                .maxWaitMinutes(maxWait)
                .destinationArea("Urgencias")
                .phone("573001112233")
                .present(true)
                .build();
    }

    @Test
    @DisplayName("WN-01: La espera se mide desde la llegada aunque lo hayan reevaluado hace poco")
    void wn01_esperaDesdeLlegada() {
        RankedPatientDTO p = WaitClock.stamp(waiting("Ana Ruiz", 50, 45, 30), now);

        assertThat(p.getWaitedMinutes()).isEqualTo(45);
        assertThat(p.getMinutesLeft()).isEqualTo(-15);
        assertThat(p.getOverdue()).isTrue();
    }

    @Test
    @DisplayName("WN-02: Con el mismo riesgo pasa primero quien llegó antes")
    void wn02_empateLoGanaQuienLlegoAntes() {
        RankedPatientDTO late = waiting("Llegó tarde", 60, 5, 30);
        RankedPatientDTO early = waiting("Llegó temprano", 60, 25, 30);
        RankedPatientDTO critical = waiting("Crítico", 90, 1, 10);

        List<RankedPatientDTO> rows = new ArrayList<>(List.of(late, early, critical));
        rows.sort(QueueService.PRIORITY);

        assertThat(rows).extracting(RankedPatientDTO::getName)
                .containsExactly("Crítico", "Llegó temprano", "Llegó tarde");
    }

    @Test
    @DisplayName("WN-03: Se avisa cuando faltan pocos minutos o cuando está entre los primeros, una sola vez")
    void wn03_cuandoAvisar() {
        RankedPatientDTO almostUp = WaitClock.stamp(waiting("Ana", 40, 22, 30), now);
        almostUp.setQueuePosition(6);
        RankedPatientDTO nearFront = WaitClock.stamp(waiting("Luis", 40, 2, 60), now);
        nearFront.setQueuePosition(2);
        RankedPatientDTO plenty = WaitClock.stamp(waiting("Eva", 40, 2, 60), now);
        plenty.setQueuePosition(5);
        RankedPatientDTO alreadyNotified = WaitClock.stamp(waiting("Juan", 40, 28, 30), now);
        alreadyNotified.setQueuePosition(1);
        alreadyNotified.setNotifiedAt(now.minusMinutes(3));
        RankedPatientDTO noPhone = WaitClock.stamp(waiting("Sol", 40, 28, 30), now);
        noPhone.setQueuePosition(1);
        noPhone.setPhone(null);

        assertThat(WhatsAppNotifier.due(almostUp, 10, 2)).isTrue();
        assertThat(WhatsAppNotifier.due(nearFront, 10, 2)).isTrue();
        assertThat(WhatsAppNotifier.due(plenty, 10, 2)).isFalse();
        assertThat(WhatsAppNotifier.due(alreadyNotified, 10, 2)).isFalse();
        assertThat(WhatsAppNotifier.due(noPhone, 10, 2)).isFalse();
    }

    @Test
    @DisplayName("WN-04: Quien no ha llegado recibe un mensaje distinto que le pide presentarse")
    void wn04_mensajeSegunPresencia() {
        RankedPatientDTO inRoom = WaitClock.stamp(waiting("Ana María Ruiz", 40, 22, 30), now);
        inRoom.setQueuePosition(2);
        RankedPatientDTO away = WaitClock.stamp(waiting("Luis Pérez", 40, 22, 30), now);
        away.setQueuePosition(3);
        away.setPresent(false);

        assertThat(WhatsAppNotifier.message(inRoom))
                .startsWith("Hola Ana,")
                .contains("turno es AM-478")
                .contains("puesto 2")
                .contains("Permanezca en la sala");
        assertThat(WhatsAppNotifier.message(away))
                .startsWith("Hola Luis,")
                .contains("turno es LP-478")
                .contains("aún no registramos su llegada")
                .contains("8 minutos");
    }

    @Test
    @DisplayName("WN-05: Con el mismo nivel, 65+ pasa antes que un joven; si el joven está más grave, él va primero")
    void wn05_adultoMayorNoSaltaAQuienEstaMasGrave() {
        RankedPatientDTO elderSame = waiting("Marta López", 42, 5, 30);
        elderSame.setAge(72);
        RankedPatientDTO youngSame = waiting("Pedro Díaz", 48, 20, 30);
        youngSame.setAge(22);
        RankedPatientDTO youngCritical = waiting("Laura Ruiz", 90, 1, 10);
        youngCritical.setAge(19);
        youngCritical.setTriageLevel(TriageLevel.TRIAGE_I_ROJO);

        List<RankedPatientDTO> rows = new ArrayList<>(List.of(youngSame, elderSame, youngCritical));
        rows.sort(QueueService.PRIORITY);

        assertThat(rows).extracting(RankedPatientDTO::getName)
                .containsExactly("Laura Ruiz", "Marta López", "Pedro Díaz");
    }
}
