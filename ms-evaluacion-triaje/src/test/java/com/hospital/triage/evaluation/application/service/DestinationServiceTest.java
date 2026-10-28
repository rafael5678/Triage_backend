package com.hospital.triage.evaluation.application.service;

import com.hospital.triage.common.enums.CareArea;
import com.hospital.triage.common.enums.TriageLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationServiceTest {

    private final DestinationService destinations = new DestinationService();

    @Test
    @DisplayName("DS-01: Cada nivel de triaje va a su área con su tiempo máximo")
    void ds01_nivelDefineArea() {
        assertThat(destinations.decide(TriageLevel.TRIAGE_I_ROJO, "cefalea", 40, null).area()).isEqualTo(CareArea.REANIMACION);
        assertThat(destinations.decide(TriageLevel.TRIAGE_II_NARANJA, "cefalea", 40, null).area()).isEqualTo(CareArea.URGENCIAS_CRITICAS);
        assertThat(destinations.decide(TriageLevel.TRIAGE_III_AMARILLO, "cefalea", 40, null).area()).isEqualTo(CareArea.URGENCIAS);
        assertThat(destinations.decide(TriageLevel.TRIAGE_IV_VERDE, "cefalea", 40, null).area()).isEqualTo(CareArea.CONSULTA_PRIORITARIA);
        var leve = destinations.decide(TriageLevel.TRIAGE_V_AZUL, "cura", 40, null);
        assertThat(leve.area()).isEqualTo(CareArea.CONSULTA_EXTERNA);
        assertThat(leve.maxWaitMinutes()).isEqualTo(120);
        assertThat(leve.code()).isNull();
    }

    @Test
    @DisplayName("DS-02: Dolor de pecho activa código infarto y sube al menos a urgencias críticas")
    void ds02_codigoInfarto() {
        var d = destinations.decide(TriageLevel.TRIAGE_III_AMARILLO, "sca", 60, null);
        assertThat(d.code()).isEqualTo("Código infarto");
        assertThat(d.area()).isEqualTo(CareArea.URGENCIAS_CRITICAS);
        assertThat(d.maxWaitMinutes()).isEqualTo(10);
    }

    @Test
    @DisplayName("DS-03: Un código no baja de área a un paciente crítico")
    void ds03_codigoNoBajaArea() {
        var d = destinations.decide(TriageLevel.TRIAGE_I_ROJO, "avc", 70, null);
        assertThat(d.area()).isEqualTo(CareArea.REANIMACION);
        assertThat(d.code()).isEqualTo("Código ACV");
    }

    @Test
    @DisplayName("DS-04: La IA puede subir de área pero nunca bajarla")
    void ds04_iaSoloSube() {
        assertThat(destinations.decide(TriageLevel.TRIAGE_IV_VERDE, "abdomen", 30, "URGENCIAS").area())
                .isEqualTo(CareArea.URGENCIAS);
        assertThat(destinations.decide(TriageLevel.TRIAGE_II_NARANJA, "abdomen", 30, "CONSULTA_EXTERNA").area())
                .isEqualTo(CareArea.URGENCIAS_CRITICAS);
        assertThat(destinations.decide(TriageLevel.TRIAGE_IV_VERDE, "abdomen", 30, "no-existe").area())
                .isEqualTo(CareArea.CONSULTA_PRIORITARIA);
    }

    @Test
    @DisplayName("DS-05: Menores de 14 años quedan marcados para pediatría")
    void ds05_pediatria() {
        assertThat(destinations.decide(TriageLevel.TRIAGE_IV_VERDE, "fiebre", 8, null).code()).isEqualTo("Pediatría");
        assertThat(destinations.decide(TriageLevel.TRIAGE_II_NARANJA, "trauma", 10, null).code()).isEqualTo("Código trauma · Pediatría");
    }
}
