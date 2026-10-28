package com.hospital.triage.evaluation.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.VitalSignsDTO;
import com.hospital.triage.common.enums.CareArea;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;
import com.hospital.triage.evaluation.application.port.PatientSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corre contra un Postgres real con el esquema de producción. Si una entidad apunta a una columna
 * que no existe (como pasó con temperature/status), Hibernate falla al validar y la prueba no pasa.
 * Necesita Docker: en GitHub Actions corre siempre; en un equipo sin Docker se salta.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema-triage.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EvaluationStore.class, EvaluationStoreIntegrationTest.Beans.class})
class EvaluationStoreIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class Beans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Autowired
    private EvaluationStore store;

    @Autowired
    private JdbcTemplate jdbc;

    private PatientSnapshot patient() {
        UUID id = jdbc.queryForObject(
                "INSERT INTO pacientes (nombre, documento, edad, sexo, lesion_id) VALUES ('Ana Ruiz', ?, 34, 'F', 'sca') RETURNING id",
                UUID.class, "DOC-" + UUID.randomUUID());
        return new PatientSnapshot(id, "Ana Ruiz", "DOC", 34, "F", "sca", "Dolor de pecho", "", "573001112233", false, null);
    }

    private VitalSignsDTO vitals(UUID patientId, int heartRate, String by) {
        VitalSignsDTO dto = new VitalSignsDTO();
        dto.setPatientId(patientId);
        dto.setHeartRate(heartRate);
        dto.setSpo2(95);
        dto.setSystolic(120);
        dto.setDiastolic(80);
        dto.setTemperature(BigDecimal.valueOf(36.8));
        dto.setRespiratoryRate(18);
        dto.setGlucose(110);
        dto.setPainScale(6);
        dto.setConsciousness("A");
        dto.setEvaluatedBy(by);
        return dto;
    }

    private DestinationService.Destination destination() {
        return new DestinationService.Destination(CareArea.URGENCIAS_CRITICAS, "Código infarto", "Nivel II", 10);
    }

    private TriageService.RiskResult risk(double score) {
        return new TriageService.RiskResult(BigDecimal.valueOf(score), TriageLevel.TRIAGE_II_NARANJA, 3);
    }

    @Test
    @DisplayName("IT-01: Guarda una evaluación completa con las columnas reales del esquema")
    void it01_guardaEvaluacion() {
        PatientSnapshot p = patient();

        RankedPatientDTO saved = store.save(p, vitals(p.id(), 110, "Enfermería"), risk(70), AiAssistDTO.builder().active(false).build(), destination());

        assertThat(saved.getStatus()).isEqualTo(CareStatus.EN_ESPERA);
        assertThat(saved.getTemperature()).isEqualByComparingTo(BigDecimal.valueOf(36.8));
        assertThat(saved.getGlucose()).isEqualTo(110);
        assertThat(saved.getDestinationCode()).isEqualTo("Código infarto");
        assertThat(saved.getEvaluatedBy()).isEqualTo("Enfermería");
        assertThat(saved.getArrivedAt()).isNotNull();
        assertThat(saved.getPresent()).isTrue();
    }

    @Test
    @DisplayName("IT-02: Reevaluar guarda historial y conserva llegada, presencia y aviso de la misma visita")
    void it02_reevaluarGuardaHistorial() {
        PatientSnapshot p = patient();
        VitalSignsDTO first = vitals(p.id(), 100, "Turno mañana");
        first.setPresent(false);
        RankedPatientDTO a = store.save(p, first, risk(55), AiAssistDTO.builder().active(false).build(), destination());
        store.markNotified(p.id(), "WHATSAPP");

        RankedPatientDTO b = store.save(p, vitals(p.id(), 128, "Turno tarde"), risk(72), AiAssistDTO.builder().active(false).build(), destination());

        List<RankedPatientDTO> history = store.history(p.id());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getHeartRate()).isEqualTo(100);
        assertThat(history.get(1).getHeartRate()).isEqualTo(128);
        assertThat(b.getArrivedAt()).isEqualTo(a.getArrivedAt());
        assertThat(b.getPresent()).isFalse();
        assertThat(b.getNotifiedChannel()).isEqualTo("WHATSAPP");
        assertThat(store.loadByStatus(CareStatus.EN_ESPERA))
                .filteredOn(r -> p.id().equals(r.getPatientId()))
                .singleElement()
                .satisfies(r -> assertThat(r.getRiskScore()).isEqualByComparingTo(BigDecimal.valueOf(72)));
        Integer vigentes = jdbc.queryForObject(
                "SELECT count(*) FROM evaluaciones_triage WHERE paciente_id = ? AND vigente", Integer.class, p.id());
        assertThat(vigentes).isEqualTo(1);
    }

    @Test
    @DisplayName("IT-03: Pasar a atención registra la hora real y una visita nueva vuelve a empezar en espera")
    void it03_atencionYVisitaNueva() {
        PatientSnapshot p = patient();
        store.save(p, vitals(p.id(), 100, "A"), risk(55), AiAssistDTO.builder().active(false).build(), destination());

        RankedPatientDTO inCare = store.updateStatus(p.id(), CareStatus.EN_ATENCION);
        assertThat(inCare.getCareStartedAt()).isNotNull();
        assertThat(inCare.getQueuePosition()).isNull();

        store.updateStatus(p.id(), CareStatus.ATENDIDO);
        RankedPatientDTO newVisit = store.save(p, vitals(p.id(), 90, "B"), risk(30), AiAssistDTO.builder().active(false).build(), destination());
        assertThat(newVisit.getStatus()).isEqualTo(CareStatus.EN_ESPERA);
        assertThat(newVisit.getCareStartedAt()).isNull();
    }
}
