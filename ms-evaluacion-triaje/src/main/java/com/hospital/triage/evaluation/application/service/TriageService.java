package com.hospital.triage.evaluation.application.service;

import com.hospital.triage.common.TriageBands;
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
import java.util.Set;

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

    /**
     * Motivos que sí pueden forzar banda alta aunque los vitals se vean estables.
     * Trauma is NOT here: the dropdown "golpe o accidente fuerte" is often a bee sting
     * or a bruise. Claude + the story decide; only true crash/fall pins a floor.
     */
    private static final Set<String> EMERGENCY_MOTIFS = Set.of(
            "paro", "avc", "sca", "disnea", "sepsis", "anafilaxia", "hemorragia", "convulsion"
    );

    public record RiskResult(BigDecimal score, TriageLevel level, int news2) {}

    public RiskResult calculate(int age, int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                                int respiratoryRate, String conditionId, String history) {
        return calculate(age, heartRate, spo2, systolic, diastolic, temperature, respiratoryRate,
                conditionId, history, null, null, null);
    }

    /**
     * glucose en mg/dL, painScale 0-10 y consciousness en AVPU son opcionales.
     * Solo pueden subir el score: un dato ausente no resta.
     */
    public RiskResult calculate(int age, int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                                int respiratoryRate, String conditionId, String history,
                                Integer glucose, Integer painScale, String consciousness) {
        return calculate(age, heartRate, spo2, systolic, diastolic, temperature, respiratoryRate,
                conditionId, history, glucose, painScale, consciousness, false, null, List.of());
    }

    /**
     * Menores de 16 años: NEWS2 solo está validado en adultos, así que los vitals se puntúan contra los
     * rangos normales de su edad (al estilo PEWS) y los pisos de emergencia also son pediátricos.
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
        return calculate(age, heartRate, spo2, systolic, diastolic, temperature, respiratoryRate,
                conditionId, history, glucose, painScale, consciousness, pregnant, gestationWeeks,
                estimatedFields, null);
    }

    public RiskResult calculate(int age, int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                                int respiratoryRate, String conditionId, String history,
                                Integer glucose, Integer painScale, String consciousness,
                                boolean pregnant, Integer gestationWeeks, List<String> estimatedFields,
                                Integer catalogSeverity) {
        return calculate(age, heartRate, spo2, systolic, diastolic, temperature, respiratoryRate,
                conditionId, history, glucose, painScale, consciousness, pregnant, gestationWeeks,
                estimatedFields, catalogSeverity, null);
    }

    public RiskResult calculate(int age, int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                                int respiratoryRate, String conditionId, String history,
                                Integer glucose, Integer painScale, String consciousness,
                                boolean pregnant, Integer gestationWeeks, List<String> estimatedFields,
                                Integer catalogSeverity, String reason) {
        validate(heartRate, spo2, systolic, diastolic, temperature, respiratoryRate);
        java.util.Set<String> estimated = estimatedFields == null ? java.util.Set.of() : new java.util.HashSet<>(estimatedFields);
        String avpu = avpu(consciousness);
        PediatricBand band = PediatricBand.forAge(age);
        int spo2Score = Math.min(100, Math.max(0, spo2));
        int vitalsScore = band == null
                ? news2(respiratoryRate, spo2Score, temperature.doubleValue(), systolic, heartRate)
                : band.score(respiratoryRate, spo2Score, temperature.doubleValue(), systolic, heartRate);
        int news = vitalsScore + consciousnessPoints(avpu);
        int condition = conditionSeverity(conditionId, catalogSeverity);
        boolean crisis = measuredCrisis(estimated, spo2Score, heartRate, systolic, temperature, avpu, band);
        boolean majorTrauma = "trauma".equals(conditionId) && majorMechanism(reason, history);
        boolean localInjury = localInjury(reason, history);
        if ((("trauma".equals(conditionId) && !majorTrauma) || localInjury) && !crisis) {
            condition = Math.min(condition, 38);
        }
        int comorb = scoreHistory(history);

        double shockTerm = band == null
                ? Math.max(0, Math.min(25, (heartRate / (double) Math.max(40, systolic) - 0.5) * 40))
                : Math.min(25, Math.max(0, heartRate / (double) band.hrMax() - 1) * 60);
        double ageTerm = band == null ? Math.min(12, age / 10.0) * 0.35 : (age < 1 ? 3 : 0);

        double score = condition * 0.38 + (Math.min(20, news) / 20.0) * 100 * 0.42
                + shockTerm
                + ageTerm
                + comorb * 0.55;
        // Catalog floor: chest/arrest/stroke yes. Trauma only if the story is crash/height.
        int catalogFloor = esiFloor(condition);
        String motif = conditionId == null ? "" : conditionId;
        boolean emergencyFloor = (EMERGENCY_MOTIFS.contains(motif) || majorTrauma) && !(localInjury && !crisis);
        if (!emergencyFloor && !crisis) {
            catalogFloor = 0;
        }
        score = Math.max(score, catalogFloor);

        if (painScale != null && painScale >= 8) {
            score += 4;
        }
        // In children, extremes are scored against age (PEWS), not adult ranges.
        if (band != null && band.extreme(heartRate, respiratoryRate, systolic)) {
            score = Math.max(score, TriageBands.RESUSCITATION);
        }
        if (!estimated.contains("heartRate") && heartRate > 0 && band == null) {
            if (heartRate < 40) {
                score = Math.max(score, TriageBands.RESUSCITATION);
            } else if (heartRate < 45) {
                score = Math.max(score, TriageBands.ESI_I);
            } else if (heartRate < 50) {
                score = Math.max(score, TriageBands.ESI_II);
            }
        }
        if ((!estimated.contains("systolic") && systolic >= 180)
                || (!estimated.contains("diastolic") && diastolic >= 120)) {
            score = Math.max(score, TriageBands.ESI_III);
        }
        if (!estimated.contains("systolic") && systolic >= 200) {
            score = Math.max(score, TriageBands.ESI_II);
        }
        if (!estimated.contains("systolic") && !estimated.contains("diastolic") && systolic < diastolic) {
            score = Math.max(score, TriageBands.ESI_II);
        }
        if (!estimated.contains("systolic") && systolic > 0 && systolic < 90) {
            score = Math.max(score, TriageBands.ESI_III);
        }
        AgeNorms norms = AgeNorms.of(age, pregnant);
        if (!estimated.contains("spo2")) {
            if (spo2Score <= 85) {
                score = Math.max(score, TriageBands.RESUSCITATION);
            } else if (spo2Score <= 89) {
                score = Math.max(score, TriageBands.ESI_I);
            } else if (spo2Score <= 91) {
                score = Math.max(score, TriageBands.ESI_II);
            } else if (spo2Score < 95) {
                score = Math.max(score, TriageBands.ESI_IV);
            }
        }
        if (!estimated.contains("temperature") && temperature != null) {
            double t = temperature.doubleValue();
            if (t <= 34.0) {
                score = Math.max(score, TriageBands.RESUSCITATION);
            } else if (t <= 35.0) {
                score = Math.max(score, TriageBands.ESI_I);
            } else if (t <= 35.5) {
                score = Math.max(score, TriageBands.ESI_III);
            }
        }
        if (!estimated.contains("heartRate") && norms.hrOff(heartRate)) {
            score = Math.max(score, norms.hrFar(heartRate) ? TriageBands.ESI_II : TriageBands.ESI_III);
        }
        if (!estimated.contains("respiratoryRate") && norms.rrOff(respiratoryRate)) {
            score = Math.max(score, TriageBands.ESI_III);
        }
        if (pregnant) {
            if (systolic >= 160 || diastolic >= 110) {
                score = Math.max(score, TriageBands.ESI_I);
            } else if (systolic >= 140 || diastolic >= 90) {
                score = Math.max(score, TriageBands.ESI_II);
            }
            if ("hemorragia".equals(conditionId) || "abdomen".equals(conditionId) || "trauma".equals(conditionId)
                    || (gestationWeeks != null && gestationWeeks >= 20 && "convulsion".equals(conditionId))) {
                score = Math.max(score, TriageBands.ESI_II);
            }
        }
        if ("P".equals(avpu) || "U".equals(avpu)) {
            score = Math.max(score, TriageBands.RESUSCITATION);
        } else if ("V".equals(avpu)) {
            score = Math.max(score, TriageBands.ESI_I);
        }
        if (glucose != null) {
            if (glucose < 54) {
                score = Math.max(score, TriageBands.RESUSCITATION);
            } else if (glucose > 600) {
                score = Math.max(score, TriageBands.ESI_I);
            } else if (glucose < 70 || glucose > 400) {
                score = Math.max(score, TriageBands.ESI_II);
            }
        }
        if ("paro".equals(conditionId)) {
            score = Math.max(score, TriageBands.CARDIAC_ARREST);
        }
        score = Math.min(TriageBands.MAX, Math.max(TriageBands.MIN, score));
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
        double suggested = Math.min(TriageBands.MAX, Math.max(TriageBands.MIN, ia.getSuggestedScore()));
        boolean sickVitals = protocol.news2() >= 5 || base >= TriageBands.RESUSCITATION;
        double fused;
        if (suggested >= base) {
            fused = suggested;
        } else if (sickVitals) {
            fused = base;
        } else {
            fused = suggested;
        }
        fused = Math.min(TriageBands.MAX, Math.max(TriageBands.MIN, fused));
        BigDecimal rounded = BigDecimal.valueOf(fused).setScale(1, RoundingMode.HALF_UP);
        return new RiskResult(rounded, levelFrom(fused), protocol.news2());
    }

    private int conditionSeverity(String conditionId, Integer catalogSeverity) {
        if (conditionId == null || conditionId.isBlank()) {
            return clampSeverity(catalogSeverity, 40);
        }
        return symptoms.severityOf(conditionId)
                .or(() -> catalogSeverity != null ? java.util.Optional.of(clampSeverity(catalogSeverity, catalogSeverity)) : java.util.Optional.empty())
                .orElseGet(() -> CONDITION_SEVERITY.getOrDefault(conditionId, 40));
    }

    private static int clampSeverity(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.min(TriageBands.MAX, Math.max(TriageBands.MIN, value));
    }

    private static boolean measuredCrisis(java.util.Set<String> estimated, int spo2, int hr, int sbp,
                                          BigDecimal temperature, String avpu, PediatricBand band) {
        if (!estimated.contains("spo2") && spo2 < 95) {
            return true;
        }
        if (!estimated.contains("heartRate") && hr > 0 && (hr < 50 || (band == null && hr > 130))) {
            return true;
        }
        if (!estimated.contains("systolic") && sbp > 0 && (sbp < 90 || sbp >= 180)) {
            return true;
        }
        if (!estimated.contains("temperature") && temperature != null) {
            double t = temperature.doubleValue();
            if (t <= 35.5 || t >= 39) {
                return true;
            }
        }
        return "P".equals(avpu) || "U".equals(avpu) || "V".equals(avpu);
    }

    /** Sting, bruise or swollen eye: not sepsis and not shock room. Anaphylaxis (face, wheeze, not breathing) still is. */
    static boolean localInjury(String reason, String history) {
        String t = ((reason == null ? "" : reason) + " " + (history == null ? "" : history)).toLowerCase();
        if (t.isBlank()) {
            return false;
        }
        if (t.contains("no respira") || t.contains("sibilanc") || t.contains("hinchazón de cara")
                || t.contains("hinchazon de cara") || t.contains("lengua hinch") || t.contains("no responde")) {
            return false;
        }
        return t.contains("picadura") || t.contains("abeja") || t.contains("avispa")
                || t.contains("moreton") || t.contains("moretón");
    }

    /** Crash, fall from height, polytrauma. A sting or bruise does not count. */
    static boolean majorMechanism(String reason, String history) {
        String t = ((reason == null ? "" : reason) + " " + (history == null ? "" : history)).toLowerCase();
        if (t.isBlank()) {
            return false;
        }
        String[] keys = {
                "altura", "segundo piso", "tercer piso", "andamio", "choque", "volcad", "volcam",
                "moto", "automovil", "automóvil", "atropell", "politrauma",
                "arma", "puñal", "apunal", "bala", "disparo", "expulsad",
                "craneo", "cráneo", "tce", "pelvis",
                "inconscien", "no responde", "perdio el conocimiento", "perdió el conocimiento",
                "sangrado abundante", "caida de", "caída de", "aplast"
        };
        for (String key : keys) {
            if (t.contains(key)) {
                return true;
            }
        }
        return false;
    }

    static int esiFloor(int conditionSeverity) {
        return TriageBands.esiFloor(conditionSeverity);
    }

    private void validate(int fc, int spo2, int pas, int pad, BigDecimal temp, int fr) {
        if (spo2 < 0 || spo2 > 190) {
            throw new InvalidVitalSignsException("SpO2 out of range 0-190");
        }
        if (temp == null) {
            throw new InvalidVitalSignsException("Temperature is required");
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

    /** NEWS2 adds 3 points for any consciousness other than alert (C, V, P or U). */
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

    private int scoreHistory(String history) {
        if (history == null) return 0;
        String t = history.toLowerCase();
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
        return TriageBands.fromScore(score);
    }
}
