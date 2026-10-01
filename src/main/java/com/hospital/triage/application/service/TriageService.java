package com.hospital.triage.application.service;

import com.hospital.triage.domain.enums.NivelTriage;
import com.hospital.triage.domain.exception.LecturaSignosInvalidosException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Matriz de riesgo inmediato: NEWS2 + gravedad de lesión/enfermedad + comorbilidades.
 * El score continuo (0–100) alimenta el reordenamiento dinámico de la cola.
 */
@Service
public class TriageService {

    private static final Map<String, Integer> GRAVEDAD_LESION = Map.ofEntries(
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

    public record ResultadoCriticidad(BigDecimal score, NivelTriage nivel, int news2) {}

    public ResultadoCriticidad calcular(int edad, int fc, int spo2, int pas, int pad, BigDecimal temp, int fr,
                                       String lesionId, String antecedentes) {
        validar(fc, spo2, pas, pad, temp, fr);
        int news = news2(fr, spo2, temp.doubleValue(), pas, fc);
        double shock = fc / (double) Math.max(40, pas);
        int lesion = GRAVEDAD_LESION.getOrDefault(lesionId == null ? "" : lesionId, 40);
        int comorb = puntuarAntecedentes(antecedentes);

        double score = lesion * 0.38 + (news / 20.0) * 100 * 0.42
                + Math.min(25, (shock - 0.5) * 40)
                + Math.min(12, edad / 10.0) * 0.35
                + comorb * 0.55;

        if (spo2 < 85 || pas < 80 || fr < 8 || fc > 150) {
            score = Math.max(score, 86);
        }
        if ("paro".equals(lesionId)) {
            score = Math.max(score, 97);
        }
        score = Math.min(100, Math.max(0, score));
        BigDecimal rounded = BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP);
        return new ResultadoCriticidad(rounded, nivelDe(score), news);
    }

    private void validar(int fc, int spo2, int pas, int pad, BigDecimal temp, int fr) {
        if (spo2 < 0 || spo2 > 100) {
            throw new LecturaSignosInvalidosException("SpO2 fuera de rango 0-100");
        }
        if (temp == null) {
            throw new LecturaSignosInvalidosException("Temperatura requerida");
        }
        if (pas < pad) {
            throw new LecturaSignosInvalidosException("La sistólica no puede ser menor que la diastólica");
        }
        if (fc < 20 || fr < 4) {
            throw new LecturaSignosInvalidosException("Constantes vitales irreales");
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

    private int puntuarAntecedentes(String texto) {
        if (texto == null) return 0;
        String t = texto.toLowerCase();
        int p = 0;
        if (t.contains("infarto") || t.contains("iam")) p += 10;
        if (t.contains("falla") || t.contains("icc")) p += 9;
        if (t.contains("epoc") || t.contains("asma")) p += 8;
        if (t.contains("renal")) p += 7;
        if (t.contains("diabetes")) p += 4;
        if (t.contains("hipertens")) p += 3;
        return p;
    }

    NivelTriage nivelDe(double score) {
        if (score >= 78) return NivelTriage.TRIAGE_I_ROJO;
        if (score >= 62) return NivelTriage.TRIAGE_II_NARANJA;
        if (score >= 42) return NivelTriage.TRIAGE_III_AMARILLO;
        if (score >= 22) return NivelTriage.TRIAGE_IV_VERDE;
        return NivelTriage.TRIAGE_V_AZUL;
    }
}
