package com.hospital.triage.evaluation.application.service;

import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.enums.TriageLevel;
import com.hospital.triage.common.exception.InvalidVitalSignsException;
import com.hospital.triage.evaluation.domain.entity.Symptom;
import com.hospital.triage.evaluation.infrastructure.persistence.SymptomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Immediate-risk matrix: NEWS2 + complaint severity + comorbidities.
 * Gemini may raise the score, never below the protocol floor.
 */
@Service
@RequiredArgsConstructor
public class TriageService {

    private final SymptomRepository symptoms;

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
        validate(heartRate, spo2, systolic, diastolic, temperature, respiratoryRate);
        int news = news2(respiratoryRate, spo2, temperature.doubleValue(), systolic, heartRate);
        double shock = heartRate / (double) Math.max(40, systolic);
        int condition = conditionSeverity(conditionId);
        int comorb = scoreHistory(history);

        double score = condition * 0.38 + (news / 20.0) * 100 * 0.42
                + Math.min(25, (shock - 0.5) * 40)
                + Math.min(12, age / 10.0) * 0.35
                + comorb * 0.55;

        if (spo2 < 85 || systolic < 80 || respiratoryRate < 8 || heartRate > 150) {
            score = Math.max(score, 86);
        }
        if ("paro".equals(conditionId)) {
            score = Math.max(score, 97);
        }
        score = Math.min(100, Math.max(0, score));
        BigDecimal rounded = BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP);
        return new RiskResult(rounded, levelFrom(score), news);
    }

    public RiskResult merge(RiskResult protocol, AiAssistDTO ia) {
        if (ia == null || !ia.isActive() || ia.getSuggestedScore() == null) {
            return protocol;
        }
        double base = protocol.score().doubleValue();
        double suggested = Math.min(100, Math.max(0, ia.getSuggestedScore()));
        if (suggested <= base) {
            return protocol;
        }
        double extra = Math.min(22, suggested - base);
        double fused = Math.min(100, base + extra);
        BigDecimal rounded = BigDecimal.valueOf(fused).setScale(1, RoundingMode.HALF_UP);
        return new RiskResult(rounded, levelFrom(fused), protocol.news2());
    }

    private int conditionSeverity(String conditionId) {
        if (conditionId == null || conditionId.isBlank()) {
            return 40;
        }
        return symptoms.findById(conditionId)
                .map(Symptom::getSeverity)
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

    private int scoreHistory(String texto) {
        if (texto == null) return 0;
        String t = texto.toLowerCase();
        int p = 0;
        if (t.contains("infarto") || t.contains("iam")) p += 10;
        if (t.contains("falla") || t.contains("icc")) p += 9;
        if (t.contains("epoc") || t.contains("asma")) p += 8;
        if (t.contains("renal")) p += 7;
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
