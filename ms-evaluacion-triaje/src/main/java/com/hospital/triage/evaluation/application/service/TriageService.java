package com.hospital.triage.evaluation.application.service;

import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.enums.TriageLevel;
import com.hospital.triage.common.exception.InvalidVitalSignsException;
import com.hospital.triage.evaluation.application.port.SymptomSeverityPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Immediate-risk matrix: NEWS2 + complaint severity + comorbidities.
 * Gemini may raise the score, never below the protocol floor.
 */
@Service
@RequiredArgsConstructor
public class TriageService {

    private final SymptomSeverityPort symptoms;

    private static final Map<String, Integer> CONDITION_SEVERITY = Map.ofEntries(
            Map.entry("paro", 99),
            Map.entry("avc", 94),
            Map.entry("sca", 88),
            Map.entry("trauma", 90),
            Map.entry("disnea", 82),
            Map.entry("sepsis", 80),
            Map.entry("anafilaxia", 86),
            Map.entry("hemorragia", 84),
            Map.entry("abdomen", 52),
            Map.entry("convulsion", 70),
            Map.entry("fractura", 38),
            Map.entry("quemadura", 48),
            Map.entry("cefalea", 26),
            Map.entry("fiebre", 24),
            Map.entry("gi", 18),
            Map.entry("cura", 8)
    );

    public record RiskResult(BigDecimal score, TriageLevel level, int news2) {}

    public RiskResult calculate(int age, int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                                int respiratoryRate, String conditionId, String history) {
        return calculate(age, heartRate, spo2, systolic, diastolic, temperature, respiratoryRate,
                conditionId, history, null, null, null);
    }

    /**
     * glucose en mg/dL, painScale 0-10 y consciousness en AVPU son opcionales.
     * Solo pueden subir el puntaje: un dato ausente no resta.
     */
    public RiskResult calculate(int age, int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                                int respiratoryRate, String conditionId, String history,
                                Integer glucose, Integer painScale, String consciousness) {
        return calculate(age, heartRate, spo2, systolic, diastolic, temperature, respiratoryRate,
                conditionId, history, glucose, painScale, consciousness, false, null, List.of());
    }

    /**
     * Menores de 16 años: NEWS2 solo está validado en adultos, así que los signos se puntúan contra los
     * rangos normales de su edad (al estilo PEWS) y los pisos de emergencia también son pediátricos.
     * Embarazo: la presión alta y los motivos obstétricos de riesgo suben el piso.
     */
    public RiskResult calculate(int age, int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                                int respiratoryRate, String conditionId, String history,
                                Integer glucose, Integer painScale, String consciousness,
                                boolean pregnant, Integer gestationWeeks) {
        return calculate(age, heartRate, spo2, systolic, diastolic, temperature, respiratoryRate,
                conditionId, history, glucose, painScale, consciousness, pregnant, gestationWeeks, List.of());
    }

    public RiskResult calculate(int age, int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                                int respiratoryRate, String conditionId, String history,
                                Integer glucose, Integer painScale, String consciousness,
                                boolean pregnant, Integer gestationWeeks, List<String> estimatedFields) {
        validate(heartRate, spo2, systolic, diastolic, temperature, respiratoryRate);
        java.util.Set<String> estimated = estimatedFields == null ? java.util.Set.of() : new java.util.HashSet<>(estimatedFields);
        String avpu = avpu(consciousness);
        PediatricBand band = PediatricBand.forAge(age);
        int vitalsScore = band == null
                ? news2(respiratoryRate, spo2, temperature.doubleValue(), systolic, heartRate)
                : band.score(respiratoryRate, spo2, temperature.doubleValue(), systolic, heartRate);
        int news = vitalsScore + consciousnessPoints(avpu);
        int condition = conditionSeverity(conditionId);
        int comorb = scoreHistory(history);

        double shockTerm = band == null
                ? Math.max(0, Math.min(25, (heartRate / (double) Math.max(40, systolic) - 0.5) * 40))
                : Math.min(25, Math.max(0, heartRate / (double) band.hrMax() - 1) * 60);
        double ageTerm = band == null ? Math.min(12, age / 10.0) * 0.35 : (age < 1 ? 3 : 0);

        double score = condition * 0.38 + (Math.min(20, news) / 20.0) * 100 * 0.42
                + shockTerm
                + ageTerm
                + comorb * 0.55;

        if (painScale != null && painScale >= 8) {
            score += 4;
        }
        boolean extremeVitals = band == null
                ? (spo2 < 85 && !estimated.contains("spo2"))
                    || (systolic < 80 && !estimated.contains("systolic"))
                    || (respiratoryRate < 8 && !estimated.contains("respiratoryRate"))
                    || (heartRate > 150 && !estimated.contains("heartRate"))
                    || (heartRate < 40 && !estimated.contains("heartRate"))
                : (spo2 < 85 && !estimated.contains("spo2")) || band.extreme(heartRate, respiratoryRate, systolic);
        if (extremeVitals && estimated.isEmpty()) {
            score = Math.max(score, 86);
        } else if (extremeVitals) {
            score = Math.max(score, 62);
        }
        // Una herida chica no puede dejar el caso en "mínima" si el pulso o la presión ya están mal.
        if (!estimated.contains("heartRate") && heartRate > 0 && heartRate < 50) {
            score = Math.max(score, 62);
        }
        if ((!estimated.contains("systolic") && systolic >= 180)
                || (!estimated.contains("diastolic") && diastolic >= 120)) {
            score = Math.max(score, 42);
        }
        if (!estimated.contains("systolic") && systolic >= 200) {
            score = Math.max(score, 62);
        }
        AgeNorms norms = AgeNorms.of(age);
        if (!estimated.contains("spo2")) {
            if (spo2 < 90) {
                score = Math.max(score, 62);
            } else if (spo2 < 94) {
                score = Math.max(score, 22);
            }
        }
        if (!estimated.contains("heartRate") && norms.hrOff(heartRate)) {
            score = Math.max(score, norms.hrFar(heartRate) ? 62 : 42);
        }
        if (!estimated.contains("respiratoryRate") && norms.rrOff(respiratoryRate)) {
            score = Math.max(score, 42);
        }
        if (pregnant) {
            if (systolic >= 160 || diastolic >= 110) {
                score = Math.max(score, 78);
            } else if (systolic >= 140 || diastolic >= 90) {
                score = Math.max(score, 62);
            }
            if ("hemorragia".equals(conditionId) || "abdomen".equals(conditionId) || "trauma".equals(conditionId)
                    || (gestationWeeks != null && gestationWeeks >= 20 && "convulsion".equals(conditionId))) {
                score = Math.max(score, 62);
            }
        }
        if ("P".equals(avpu) || "U".equals(avpu)) {
            score = Math.max(score, 86);
        } else if ("V".equals(avpu)) {
            score = Math.max(score, 78);
        }
        if (glucose != null) {
            if (glucose < 54) {
                score = Math.max(score, 86);
            } else if (glucose > 600) {
                score = Math.max(score, 78);
            } else if (glucose < 70 || glucose > 400) {
                score = Math.max(score, 62);
            }
        }
        if ("paro".equals(conditionId)) {
            score = Math.max(score, 97);
        }
        score = Math.min(100, Math.max(0, score));
        BigDecimal rounded = BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP);
        return new RiskResult(rounded, levelFrom(score), news);
    }

    public RiskResult merge(RiskResult protocol, AiAssistDTO ia) {
        return merge(protocol, ia, false);
    }

    public RiskResult merge(RiskResult protocol, AiAssistDTO ia, boolean vitalsEstimated) {
        if (ia == null || !ia.isActive() || ia.getSuggestedScore() == null) {
            return protocol;
        }
        double base = protocol.score().doubleValue();
        double suggested = Math.min(100, Math.max(0, ia.getSuggestedScore()));
        if (suggested <= base) {
            return protocol;
        }
        double extra = Math.min(vitalsEstimated ? 6 : 22, suggested - base);
        double fused = Math.min(100, base + extra);
        BigDecimal rounded = BigDecimal.valueOf(fused).setScale(1, RoundingMode.HALF_UP);
        return new RiskResult(rounded, levelFrom(fused), protocol.news2());
    }

    private int conditionSeverity(String conditionId) {
        if (conditionId == null || conditionId.isBlank()) {
            return 40;
        }
        return symptoms.severityOf(conditionId)
                .orElseGet(() -> CONDITION_SEVERITY.getOrDefault(conditionId, 40));
    }

    private void validate(int fc, int spo2, int pas, int pad, BigDecimal temp, int fr) {
        if (spo2 < 0 || spo2 > 100) {
            throw new InvalidVitalSignsException("SpO2 out of range 0-100");
        }
        if (temp == null) {
            throw new InvalidVitalSignsException("Temperature is required");
        }
        if (pas < pad) {
            throw new InvalidVitalSignsException("Systolic cannot be lower than diastolic");
        }
        if (fc < 20 || fr < 4) {
            throw new InvalidVitalSignsException("Unrealistic vital signs");
        }
    }

    int news2(int rr, int spo2, double temp, int sbp, int hr) {
        int s = 0;
        if (rr <= 8) s += 3;
        else if (rr <= 11) s += 1;
        else if (rr <= 20) s += 0;
        else if (rr <= 24) s += 2;
        else s += 3;
        if (spo2 <= 91) s += 3;
        else if (spo2 <= 93) s += 2;
        else if (spo2 <= 95) s += 1;
        if (temp <= 35) s += 3;
        else if (temp <= 36) s += 1;
        else if (temp <= 38) s += 0;
        else if (temp <= 39) s += 1;
        else s += 2;
        if (sbp <= 90) s += 3;
        else if (sbp <= 100) s += 2;
        else if (sbp <= 110) s += 1;
        else if (sbp >= 220) s += 3;
        if (hr <= 40) s += 3;
        else if (hr <= 50) s += 1;
        else if (hr <= 90) s += 0;
        else if (hr <= 110) s += 1;
        else if (hr <= 130) s += 2;
        else s += 3;
        return s;
    }

    /** NEWS2 suma 3 puntos por cualquier conciencia distinta de alerta (C, V, P o U). */
    static int consciousnessPoints(String avpu) {
        return avpu == null || "A".equals(avpu) ? 0 : 3;
    }

    static String avpu(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        return value.length() == 1 && "ACVPU".contains(value) ? value : null;
    }

    private int scoreHistory(String texto) {
        if (texto == null) return 0;
        String t = texto.toLowerCase();
        int p = 0;
        if (t.contains("infarto") || t.contains("iam")) p += 10;
        if (t.contains("falla") || t.contains("icc")) p += 9;
        if (t.contains("epoc") || t.contains("asma")) p += 8;
        if (t.contains("renal") || t.contains("erc")) p += 7;
        if (t.contains("diabetes") || t.contains("dm")) p += 4;
        if (t.contains("hipertens") || t.contains("hta")) p += 3;
        if (t.contains("cancer")) p += 6;
        if (t.contains("inmuno") || t.contains("quimio") || t.contains("vih")) p += 7;
        return p;
    }

    TriageLevel levelFrom(double score) {
        if (score >= 78) return TriageLevel.TRIAGE_I_ROJO;
        if (score >= 62) return TriageLevel.TRIAGE_II_NARANJA;
        if (score >= 42) return TriageLevel.TRIAGE_III_AMARILLO;
        if (score >= 22) return TriageLevel.TRIAGE_IV_VERDE;
        return TriageLevel.TRIAGE_V_AZUL;
    }
}
