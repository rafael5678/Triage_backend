package com.hospital.triage.evaluation.application.service;

import com.hospital.triage.common.enums.CareArea;
import com.hospital.triage.common.enums.TriageLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas para niños (rangos por edad) y embarazadas (códigos obstétricos). */
class ClinicalRulesTest {

    private final TriageService triage = new TriageService(id -> Optional.empty());
    private final DestinationService destinations = new DestinationService();

    private TriageService.RiskResult score(int age, int hr, int spo2, int sbp, int dbp, double temp, int rr,
                                           String condition, boolean pregnant, Integer weeks) {
        return triage.calculate(age, hr, spo2, sbp, dbp, BigDecimal.valueOf(temp), rr, condition, "",
                null, null, "A", pregnant, weeks);
    }

    @Test
    @DisplayName("CR-01: Un niño de 2 años con FC 140 y FR 30 está en su rango; un adulto con lo mismo no")
    void cr01_rangosPediatricos() {
        var child = score(2, 140, 98, 95, 60, 37.0, 30, "fiebre", false, null);
        var adult = score(30, 140, 98, 95, 60, 37.0, 30, "fiebre", false, null);

        assertThat(child.score()).isLessThan(adult.score());
        assertThat(child.score()).isLessThan(BigDecimal.valueOf(60));
    }

    @Test
    @DisplayName("CR-02: Un lactante con FC 220 tiene signos extremos para su edad (piso 86)")
    void cr02_lactanteExtremo() {
        var infant = score(0, 220, 96, 80, 50, 37.5, 50, "fiebre", false, null);

        assertThat(infant.score()).isGreaterThanOrEqualTo(BigDecimal.valueOf(86));
        assertThat(infant.level()).isEqualTo(TriageLevel.TRIAGE_I_ROJO);
    }

    @Test
    @DisplayName("CR-03: Embarazada con presión 165/112 sube al menos a 78 y activa código preeclampsia")
    void cr03_preeclampsiaSevera() {
        var pregnant = score(28, 92, 98, 165, 112, 36.8, 18, "cefalea", true, 32);
        var notPregnant = score(28, 92, 98, 165, 112, 36.8, 18, "cefalea", false, null);

        assertThat(pregnant.score()).isGreaterThanOrEqualTo(BigDecimal.valueOf(78));
        assertThat(pregnant.score()).isGreaterThan(notPregnant.score());

        var d = destinations.decide(pregnant.level(), "cefalea", 28, null, true, 32, 165, 112);
        assertThat(d.code()).isEqualTo("Código preeclampsia");
        assertThat(d.area()).isIn(CareArea.URGENCIAS_CRITICAS, CareArea.REANIMACION);
    }

    @Test
    @DisplayName("CR-04: Antes de la semana 20 la presión alta no es preeclampsia, pero sí pide valoración obstétrica")
    void cr04_antesDeVeinteSemanas() {
        var d = destinations.decide(TriageLevel.TRIAGE_III_AMARILLO, "cefalea", 25, null, true, 12, 165, 100);

        assertThat(d.code()).isEqualTo("Obstetricia");
        assertThat(d.reason()).contains("12 semanas");
    }

    @Test
    @DisplayName("CR-05: Sangrado en el embarazo es código rojo obstétrico; convulsión es código eclampsia en reanimación")
    void cr05_codigosObstetricos() {
        var bleeding = destinations.decide(TriageLevel.TRIAGE_III_AMARILLO, "hemorragia", 30, null, true, 10, 110, 70);
        assertThat(bleeding.code()).isEqualTo("Código rojo obstétrico");
        assertThat(bleeding.area()).isEqualTo(CareArea.URGENCIAS_CRITICAS);

        var seizure = destinations.decide(TriageLevel.TRIAGE_II_NARANJA, "convulsion", 30, null, true, 34, 130, 85);
        assertThat(seizure.code()).isEqualTo("Código eclampsia");
        assertThat(seizure.area()).isEqualTo(CareArea.REANIMACION);
    }

    @Test
    @DisplayName("CR-06: Sangrado en una embarazada pesa más que en otra paciente con los mismos signos")
    void cr06_sangradoEmbarazo() {
        var pregnant = score(30, 88, 98, 118, 72, 36.8, 16, "hemorragia", true, 24);

        assertThat(pregnant.score()).isGreaterThanOrEqualTo(BigDecimal.valueOf(62));
    }
}
