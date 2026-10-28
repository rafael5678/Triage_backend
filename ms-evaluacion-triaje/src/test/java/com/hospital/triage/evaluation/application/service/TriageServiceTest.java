package com.hospital.triage.evaluation.application.service;

import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.enums.TriageLevel;
import com.hospital.triage.common.exception.InvalidVitalSignsException;
import com.hospital.triage.evaluation.application.port.SymptomSeverityPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriageServiceTest {

    private final SymptomSeverityPort symptoms = id -> Optional.empty();
    private final TriageService triageService = new TriageService(symptoms);

    @Test
    @DisplayName("TC-01: Cálculo determinista de NEWS2 normal (score 0)")
    void tc01_news2Normal() {
        int news = triageService.news2(16, 98, 36.6, 120, 75);
        assertThat(news).isZero();
    }

    @Test
    @DisplayName("TC-02: Cálculo de NEWS2 con taquipnea e hipoxia (score 6)")
    void tc02_news2TaquipneaHipoxia() {
        int news = triageService.news2(28, 89, 37.0, 125, 80);
        assertThat(news).isEqualTo(6);
    }

    @Test
    @DisplayName("TC-03: Regla de seguridad de piso para condición de paro (mínimo 97)")
    void tc03_seguridadParoCardiorrespiratorio() {
        TriageService.RiskResult result = triageService.calculate(
                55, 40, 80, 70, 40, BigDecimal.valueOf(35.0), 6, "paro", ""
        );

        assertThat(result.score()).isGreaterThanOrEqualTo(BigDecimal.valueOf(97.0));
        assertThat(result.level()).isEqualTo(TriageLevel.TRIAGE_I_ROJO);
    }

    @Test
    @DisplayName("TC-04: Regla de seguridad para signos vitales extremos (piso mínimo 86)")
    void tc04_seguridadSignosVitalesExtremos() {
        TriageService.RiskResult result = triageService.calculate(
                30, 155, 84, 75, 50, BigDecimal.valueOf(37.0), 7, "cura", ""
        );

        assertThat(result.score()).isGreaterThanOrEqualTo(BigDecimal.valueOf(86.0));
        assertThat(result.level()).isEqualTo(TriageLevel.TRIAGE_I_ROJO);
    }

    @Test
    @DisplayName("TC-05: Ponderación de edad, antecedentes y comorbilidades")
    void tc05_ponderacionEdadYComorbilidades() {
        TriageService.RiskResult result = triageService.calculate(
                70, 80, 98, 120, 80, BigDecimal.valueOf(36.6), 16, "cefalea", "infarto y diabetes"
        );

        assertThat(result.news2()).isZero();
        assertThat(result.score()).isEqualTo(new BigDecimal("26.7"));
        assertThat(result.level()).isEqualTo(TriageLevel.TRIAGE_IV_VERDE);
    }

    @Test
    @DisplayName("TC-06: Fusión IA: Gemini aumenta el riesgo de forma justificada")
    void tc06_fusionGeminiAumentaRiesgo() {
        TriageService.RiskResult protocol = new TriageService.RiskResult(
                BigDecimal.valueOf(60.0), TriageLevel.TRIAGE_III_AMARILLO, 2
        );
        AiAssistDTO ia = AiAssistDTO.builder()
                .active(true)
                .suggestedScore(75.0)
                .build();

        TriageService.RiskResult fused = triageService.merge(protocol, ia);

        assertThat(fused.score()).isEqualTo(new BigDecimal("75.0"));
        assertThat(fused.level()).isEqualTo(TriageLevel.TRIAGE_II_NARANJA);
        assertThat(fused.news2()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-07: Fusión IA: Límite máximo de aumento fijado en 22 puntos")
    void tc07_fusionGeminiLimiteMaximo22Puntos() {
        TriageService.RiskResult protocol = new TriageService.RiskResult(
                BigDecimal.valueOf(50.0), TriageLevel.TRIAGE_III_AMARILLO, 2
        );
        AiAssistDTO ia = AiAssistDTO.builder()
                .active(true)
                .suggestedScore(90.0)
                .build();

        TriageService.RiskResult fused = triageService.merge(protocol, ia);

        assertThat(fused.score()).isEqualTo(new BigDecimal("72.0"));
        assertThat(fused.level()).isEqualTo(TriageLevel.TRIAGE_II_NARANJA);
    }

    @Test
    @DisplayName("TC-08: Fusión IA: Gemini nunca reduce el riesgo establecido por protocolo")
    void tc08_fusionGeminiNuncaReduceRiesgo() {
        TriageService.RiskResult protocol = new TriageService.RiskResult(
                BigDecimal.valueOf(86.0), TriageLevel.TRIAGE_I_ROJO, 5
        );
        AiAssistDTO ia = AiAssistDTO.builder()
                .active(true)
                .suggestedScore(40.0)
                .build();

        TriageService.RiskResult fused = triageService.merge(protocol, ia);

        assertThat(fused.score()).isEqualTo(new BigDecimal("86.0"));
        assertThat(fused.level()).isEqualTo(TriageLevel.TRIAGE_I_ROJO);
    }

    @Test
    @DisplayName("TC-11: Conciencia AVPU: no responde -> crítico; solo a la voz -> mínimo 78; confusión suma 3 a NEWS2")
    void tc11_conciencia() {
        BigDecimal t = BigDecimal.valueOf(36.6);
        var noResponde = triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "", null, null, "U");
        assertThat(noResponde.score()).isGreaterThanOrEqualTo(BigDecimal.valueOf(86.0));
        assertThat(noResponde.level()).isEqualTo(TriageLevel.TRIAGE_I_ROJO);

        var voz = triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "", null, null, "V");
        assertThat(voz.score()).isGreaterThanOrEqualTo(BigDecimal.valueOf(78.0));

        var confuso = triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "", null, null, "c");
        var alerta = triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "", null, null, "A");
        assertThat(confuso.news2()).isEqualTo(alerta.news2() + 3);
        assertThat(confuso.score()).isGreaterThan(alerta.score());
    }

    @Test
    @DisplayName("TC-12: Glucosa extrema sube el piso; un dato ausente no cambia el puntaje")
    void tc12_glucosa() {
        BigDecimal t = BigDecimal.valueOf(36.6);
        var base = triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "");
        var sinDatos = triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "", null, null, null);
        assertThat(sinDatos.score()).isEqualTo(base.score());

        assertThat(triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "", 45, null, null).score())
                .isGreaterThanOrEqualTo(BigDecimal.valueOf(86.0));
        assertThat(triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "", 450, null, null).score())
                .isGreaterThanOrEqualTo(BigDecimal.valueOf(62.0));
        assertThat(triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "", 110, null, null).score())
                .isEqualTo(base.score());
    }

    @Test
    @DisplayName("TC-13: Dolor de 8 a 10 suma poco; el antecedente renal 'erc' cuenta")
    void tc13_dolorYRenal() {
        BigDecimal t = BigDecimal.valueOf(36.6);
        var sinDolor = triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "", null, 3, null);
        var dolorFuerte = triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "", null, 9, null);
        assertThat(dolorFuerte.score().subtract(sinDolor.score())).isEqualByComparingTo(BigDecimal.valueOf(4.0));

        var renal = triageService.calculate(40, 80, 98, 120, 80, t, 16, "cefalea", "erc");
        assertThat(renal.score()).isGreaterThan(sinDolor.score());
    }

    @Test
    @DisplayName("TC-09: Validación fisiológica: Presión arterial incoherente (PAS < PAD)")
    void tc09_validacionPresionIncoherente() {
        assertThatThrownBy(() -> triageService.calculate(
                45, 80, 98, 70, 110, BigDecimal.valueOf(36.5), 16, "cefalea", ""
        ))
                .isInstanceOf(InvalidVitalSignsException.class)
                .hasMessageContaining("Systolic cannot be lower than diastolic");
    }

    @Test
    @DisplayName("TC-10: Validación fisiológica: Signos vitales irreales o fuera de rango")
    void tc10_validacionSignosIrreales() {
        assertThatThrownBy(() -> triageService.calculate(
                45, 80, 105, 120, 80, BigDecimal.valueOf(36.5), 16, "cefalea", ""
        ))
                .isInstanceOf(InvalidVitalSignsException.class)
                .hasMessageContaining("SpO2 out of range 0-100");

        assertThatThrownBy(() -> triageService.calculate(
                45, 15, 98, 120, 80, BigDecimal.valueOf(36.5), 16, "cefalea", ""
        ))
                .isInstanceOf(InvalidVitalSignsException.class)
                .hasMessageContaining("Unrealistic vital signs");

        assertThatThrownBy(() -> triageService.calculate(
                45, 80, 98, 120, 80, null, 16, "cefalea", ""
        ))
                .isInstanceOf(InvalidVitalSignsException.class)
                .hasMessageContaining("Temperature is required");
    }
}
