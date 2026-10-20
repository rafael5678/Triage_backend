package com.hospital.triage.evaluation.application.service;

import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.enums.TriageLevel;
import com.hospital.triage.common.exception.InvalidVitalSignsException;
import com.hospital.triage.evaluation.infrastructure.persistence.SymptomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageServiceTest {

    @Mock
    private SymptomRepository symptoms;

    private TriageService triageService;

    @BeforeEach
    void setUp() {
        triageService = new TriageService(symptoms);
    }

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
        when(symptoms.findById("paro")).thenReturn(Optional.empty());

        TriageService.RiskResult result = triageService.calculate(
                55, 40, 80, 70, 40, BigDecimal.valueOf(35.0), 6, "paro", ""
        );

        assertThat(result.score()).isGreaterThanOrEqualTo(BigDecimal.valueOf(97.0));
        assertThat(result.level()).isEqualTo(TriageLevel.TRIAGE_I_ROJO);
    }

    @Test
    @DisplayName("TC-04: Regla de seguridad para signos vitales extremos (piso mínimo 86)")
    void tc04_seguridadSignosVitalesExtremos() {
        when(symptoms.findById("cura")).thenReturn(Optional.empty());

        TriageService.RiskResult result = triageService.calculate(
                30, 155, 84, 75, 50, BigDecimal.valueOf(37.0), 7, "cura", ""
        );

        assertThat(result.score()).isGreaterThanOrEqualTo(BigDecimal.valueOf(86.0));
        assertThat(result.level()).isEqualTo(TriageLevel.TRIAGE_I_ROJO);
    }

    @Test
    @DisplayName("TC-05: Ponderación de edad, antecedentes y comorbilidades")
    void tc05_ponderacionEdadYComorbilidades() {
        when(symptoms.findById("cefalea")).thenReturn(Optional.empty());

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
