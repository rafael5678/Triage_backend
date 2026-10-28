package com.hospital.triage.ai.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.ai.domain.entity.Symptom;
import com.hospital.triage.ai.infrastructure.persistence.SymptomRepository;
import com.hospital.triage.common.dto.IntakeRequest;
import com.hospital.triage.common.dto.IntakeSuggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IntakeServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final IntakeService service = new IntakeService(new GeminiClient(json), mock(SymptomRepository.class), json);

    private static Symptom symptom(String id) {
        Symptom s = new Symptom();
        s.setId(id);
        s.setLabel(id);
        s.setSeverity(50);
        return s;
    }

    private IntakeRequest request() {
        return IntakeRequest.builder()
                .story("Señor con dolor de pecho")
                .comorbidityOptions(List.of(new IntakeRequest.Option("dm", "Diabetes"), new IntakeRequest.Option("hta", "Presión alta")))
                .build();
    }

    @Test
    @DisplayName("IN-01: Solo acepta opciones del catálogo y antecedentes de la lista")
    void in01_filtraCatalogoYAntecedentes() throws Exception {
        var answer = json.readTree("""
                {"conditionId":"inventado","reason":"Dolor torácico","comorbidityIds":["dm","otro"],"sex":"x","consciousness":"z"}
                """);

        IntakeSuggestion s = service.normalize(answer, List.of(symptom("sca")), request(), Map.of(), false);

        assertThat(s.isActive()).isTrue();
        assertThat(s.getConditionId()).isNull();
        assertThat(s.getComorbidityIds()).containsExactly("dm");
        assertThat(s.getSex()).isNull();
        assertThat(s.getConsciousness()).isNull();
    }

    @Test
    @DisplayName("IN-02: Ajusta los signos a rangos válidos y marca como estimados solo los que no escribió el personal")
    void in02_estimadosYRangos() throws Exception {
        var answer = json.readTree("""
                {"conditionId":"sca","heartRate":400,"spo2":30,"systolic":95,"temperature":45.3,"respiratoryRate":28,
                 "consciousness":"a","painScale":15,
                 "estimatedFields":["heartRate","spo2","systolic","glucose"],"photoFindings":"no hay foto"}
                """);

        IntakeSuggestion s = service.normalize(answer, List.of(symptom("sca")), request(), Map.of("systolic", 95), false);

        assertThat(s.getConditionId()).isEqualTo("sca");
        assertThat(s.getHeartRate()).isEqualTo(250);
        assertThat(s.getSpo2()).isEqualTo(50);
        assertThat(s.getTemperature()).isEqualByComparingTo(BigDecimal.valueOf(43.0));
        assertThat(s.getPainScale()).isEqualTo(10);
        assertThat(s.getConsciousness()).isEqualTo("A");
        assertThat(s.getEstimatedFields()).containsExactlyInAnyOrder("heartRate", "spo2");
        assertThat(s.getPhotoFindings()).isNull();
    }

    @Test
    @DisplayName("IN-03: Un signo marcado como estimado pero sin valor no queda marcado")
    void in03_estimadoSinValor() throws Exception {
        var answer = json.readTree("""
                {"heartRate":110,"estimatedFields":["heartRate","diastolic"],"photoFindings":"Herida de 3 cm"}
                """);

        IntakeSuggestion s = service.normalize(answer, List.of(), request(), Map.of(), true);

        assertThat(s.getEstimatedFields()).containsExactly("heartRate");
        assertThat(s.getPhotoFindings()).isEqualTo("Herida de 3 cm");
    }
}
