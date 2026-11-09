package com.hospital.triage.common;

import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.TriageStatsDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Totals for the statistics screen. Does not need patient names. */
public final class TriageStatsCalculator {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final int DEFAULT_MAX_WAIT = 30;

    private TriageStatsCalculator() {
    }

    public static TriageStatsDTO from(List<RankedPatientDTO> all, OffsetDateTime now) {
        List<RankedPatientDTO> waiting = all.stream().filter(p -> p.getStatus() == CareStatus.EN_ESPERA).toList();

        Map<String, Integer> byLevel = new LinkedHashMap<>();
        for (TriageLevel level : TriageLevel.values()) {
            byLevel.put(level.name(), 0);
        }
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (CareStatus status : CareStatus.values()) {
            byStatus.put(status.name(), 0);
        }
        Map<String, Integer> byArea = new LinkedHashMap<>();
        Map<String, Integer> byCondition = new LinkedHashMap<>();
        int withAi = 0;
        double scoreSum = 0;
        int scored = 0;
        for (RankedPatientDTO p : all) {
            if (p.getTriageLevel() != null) {
                byLevel.merge(p.getTriageLevel().name(), 1, Integer::sum);
            }
            if (p.getStatus() != null) {
                byStatus.merge(p.getStatus().name(), 1, Integer::sum);
            }
            byArea.merge(p.getDestinationArea() == null ? "Sin área" : p.getDestinationArea(), 1, Integer::sum);
            byCondition.merge(p.getConditionId() == null || p.getConditionId().isBlank() ? "sin-motivo" : p.getConditionId(), 1, Integer::sum);
            if (Boolean.TRUE.equals(p.getAiActive())) {
                withAi++;
            }
            if (p.getRiskScore() != null) {
                scoreSum += p.getRiskScore().doubleValue();
                scored++;
            }
        }

        List<Long> waits = waiting.stream()
                .filter(p -> p.getArrivedAt() != null || p.getEvaluatedAt() != null)
                .map(p -> (long) waited(p, now))
                .toList();
        int overdue = (int) waiting.stream().filter(p -> maxWait(p) - waited(p, now) < 0).count();
        List<Long> toCare = all.stream()
                .filter(p -> p.getCareStartedAt() != null)
                .map(p -> {
                    OffsetDateTime start = p.getArrivedAt() != null ? p.getArrivedAt() : p.getEvaluatedAt();
                    return start == null ? 0L : Math.max(0, Duration.between(start, p.getCareStartedAt()).toMinutes());
                })
                .toList();

        return TriageStatsDTO.builder()
                .generatedAt(now)
                .totalPatients(all.size())
                .waiting(byStatus.get(CareStatus.EN_ESPERA.name()))
                .inCare(byStatus.get(CareStatus.EN_ATENCION.name()))
                .attended(byStatus.get(CareStatus.ATENDIDO.name()))
                .averageScore(scored == 0 ? null : round(scoreSum / scored))
                .averageWaitingMinutes(waits.isEmpty() ? null : round(waits.stream().mapToLong(Long::longValue).average().orElse(0)))
                .longestWaitingMinutes(waits.isEmpty() ? null : (int) waits.stream().mapToLong(Long::longValue).max().orElse(0))
                .aiActivePercent(all.isEmpty() ? null : round(100.0 * withAi / all.size()))
                .overdue(overdue)
                .averageTimeToCareMinutes(toCare.isEmpty() ? null : round(toCare.stream().mapToLong(Long::longValue).average().orElse(0)))
                .byLevel(byLevel)
                .byArea(sortByCount(byArea))
                .byStatus(byStatus)
                .byCondition(top(sortByCount(byCondition), 8))
                .arrivalsByHour(arrivalsByHour(all, now))
                .build();
    }

    private static int waited(RankedPatientDTO p, OffsetDateTime now) {
        OffsetDateTime start = p.getArrivedAt() != null ? p.getArrivedAt() : p.getEvaluatedAt();
        if (start == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(start, now).toMinutes());
    }

    private static int maxWait(RankedPatientDTO p) {
        return p.getMaxWaitMinutes() == null ? DEFAULT_MAX_WAIT : p.getMaxWaitMinutes();
    }

    private static Map<String, Integer> arrivalsByHour(List<RankedPatientDTO> all, OffsetDateTime now) {
        ZonedDateTime end = now.atZoneSameInstant(ZONE).truncatedTo(ChronoUnit.HOURS);
        Map<String, Integer> hours = new LinkedHashMap<>();
        for (int i = 23; i >= 0; i--) {
            hours.put(label(end.minusHours(i)), 0);
        }
        for (RankedPatientDTO p : all) {
            OffsetDateTime arrived = p.getArrivedAt() != null ? p.getArrivedAt() : p.getEvaluatedAt();
            if (arrived == null || Duration.between(arrived, now).toHours() >= 24) {
                continue;
            }
            ZonedDateTime at = arrived.atZoneSameInstant(ZONE).truncatedTo(ChronoUnit.HOURS);
            hours.computeIfPresent(label(at), (k, v) -> v + 1);
        }
        return hours;
    }

    private static String label(ZonedDateTime hour) {
        return String.format("%02d:00", hour.getHour());
    }

    private static Map<String, Integer> sortByCount(Map<String, Integer> source) {
        return source.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static Map<String, Integer> top(Map<String, Integer> source, int max) {
        return source.entrySet().stream()
                .limit(max)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static Double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
