package com.hospital.triage.queue.application.service;

import com.hospital.triage.common.dto.BoardEntryDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.TriageStatsDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;
import com.hospital.triage.queue.application.port.EvaluationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InsightServiceTest {

    private final InsightService insights = new InsightService(mock(EvaluationPort.class));
    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-28T12:00:00-05:00");

    private RankedPatientDTO patient(String name, String doc, double score, TriageLevel level, CareStatus status, int minutesAgo, boolean ai) {
        return RankedPatientDTO.builder()
                .name(name).document(doc).riskScore(BigDecimal.valueOf(score)).triageLevel(level).status(status)
                .evaluatedAt(now.minusMinutes(minutesAgo)).aiActive(ai).conditionId("sca")
                .destinationArea("Urgencias generales").maxWaitMinutes(30)
                .build();
    }

    @Test
    @DisplayName("IS-01: El turno no muestra el nombre ni el documento completo")
    void is01_turnoAnonimo() {
        assertThat(InsightService.ticket("Juan Rafael Calzada", "1087782596")).isEqualTo("JR-596");
        assertThat(InsightService.ticket("Ana", "12")).isEqualTo("A-12");
        assertThat(InsightService.ticket(null, null)).isEqualTo("P");
    }

    @Test
    @DisplayName("IS-02: El tablero ordena por gravedad y descuenta lo que ya esperó")
    void is02_tablero() {
        var leve = patient("Luis Mora", "1000111", 30, TriageLevel.TRIAGE_IV_VERDE, CareStatus.EN_ESPERA, 50, false);
        var grave = patient("Eva Paz", "2000222", 80, TriageLevel.TRIAGE_I_ROJO, CareStatus.EN_ESPERA, 5, true);
        var atendido = patient("Rosa Gil", "3000333", 70, TriageLevel.TRIAGE_II_NARANJA, CareStatus.EN_ATENCION, 20, true);

        List<BoardEntryDTO> board = insights.board(List.of(leve, grave), List.of(atendido), now);

        assertThat(board).hasSize(3);
        assertThat(board.get(0).getTicket()).isEqualTo("EP-222");
        assertThat(board.get(0).getPosition()).isEqualTo(1);
        assertThat(board.get(0).getEstimatedWaitMinutes()).isEqualTo(25);
        assertThat(board.get(1).getTicket()).isEqualTo("LM-111");
        assertThat(board.get(1).getEstimatedWaitMinutes()).isZero();
        assertThat(board.get(1).getWaitedMinutes()).isEqualTo(50);
        assertThat(board.get(2).getStatus()).isEqualTo(CareStatus.EN_ATENCION);
        assertThat(board.get(2).getPosition()).isNull();
    }

    @Test
    @DisplayName("IS-03: Las estadísticas cuentan por nivel, estado, área e IA")
    void is03_estadisticas() {
        var a = patient("A B", "1", 80, TriageLevel.TRIAGE_I_ROJO, CareStatus.EN_ESPERA, 10, true);
        var b = patient("C D", "2", 40, TriageLevel.TRIAGE_IV_VERDE, CareStatus.EN_ESPERA, 30, false);
        var c = patient("E F", "3", 60, TriageLevel.TRIAGE_III_AMARILLO, CareStatus.ATENDIDO, 90, true);

        TriageStatsDTO stats = insights.stats(List.of(a, b, c), now);

        assertThat(stats.getTotalPatients()).isEqualTo(3);
        assertThat(stats.getWaiting()).isEqualTo(2);
        assertThat(stats.getAttended()).isEqualTo(1);
        assertThat(stats.getAverageScore()).isEqualTo(60.0);
        assertThat(stats.getAverageWaitingMinutes()).isEqualTo(20.0);
        assertThat(stats.getLongestWaitingMinutes()).isEqualTo(30);
        assertThat(stats.getAiActivePercent()).isEqualTo(66.7);
        assertThat(stats.getByLevel()).containsEntry("TRIAGE_I_ROJO", 1).containsEntry("TRIAGE_II_NARANJA", 0);
        assertThat(stats.getByArea()).containsEntry("Urgencias generales", 3);
        assertThat(stats.getArrivalsByHour()).hasSize(24);
        assertThat(stats.getArrivalsByHour().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
    }
}
