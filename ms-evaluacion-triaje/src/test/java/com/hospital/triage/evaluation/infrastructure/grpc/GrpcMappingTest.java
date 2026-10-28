package com.hospital.triage.evaluation.infrastructure.grpc;

import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.enums.TriageLevel;
import com.hospital.triage.evaluation.application.port.AssistInput;
import com.hospital.triage.grpc.mapping.AssistMessages;
import com.hospital.triage.grpc.mapping.RankedPatientMessages;
import com.hospital.triage.grpc.v1.AssistRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcMappingTest {

    @Test
    @DisplayName("GM-01: La foto en data URL viaja como bytes y los datos opcionales llegan a la IA")
    void gm01_assistRequestConFoto() {
        AssistRequest request = GrpcEvaluationClients.toRequest(AssistInput.builder()
                .conditionId("quemadura").reason("Se quemó con aceite").age(30).sex("F")
                .heartRate(90).spo2(98).systolic(120).diastolic(80).temperature(BigDecimal.valueOf(36.9)).respiratoryRate(18)
                .glucose(130).painScale(8).consciousness("A").estimatedFields(List.of("spo2"))
                .photoBase64("data:image/jpeg;base64,QUJD").photoMime("image/jpeg")
                .protocolScore(48.0).news2(1)
                .build());

        assertThat(request.getImage().toStringUtf8()).isEqualTo("ABC");
        assertThat(request.getImageMime()).isEqualTo("image/jpeg");
        assertThat(request.getGlucose()).isEqualTo(130);
        assertThat(request.getPainScale()).isEqualTo(8);
        assertThat(request.getConsciousness()).isEqualTo("A");
        assertThat(request.getEstimatedFieldsList()).containsExactly("spo2");
        assertThat(request.hasLocalModelScore()).isFalse();
    }

    @Test
    @DisplayName("GM-02: Sin foto ni datos opcionales no se envían campos vacíos")
    void gm02_sinFoto() {
        AssistRequest request = GrpcEvaluationClients.toRequest(AssistInput.builder()
                .age(30).heartRate(80).spo2(98).systolic(120).diastolic(80)
                .temperature(BigDecimal.valueOf(36.6)).respiratoryRate(16).protocolScore(20).news2(0)
                .build());

        assertThat(request.getImage().isEmpty()).isTrue();
        assertThat(request.hasGlucose()).isFalse();
        assertThat(request.hasPainScale()).isFalse();
    }

    @Test
    @DisplayName("GM-03: Recomendaciones, destino y estimados sobreviven el viaje por gRPC")
    void gm03_rankedPatientIdaYVuelta() {
        RankedPatientDTO original = RankedPatientDTO.builder()
                .patientId(UUID.randomUUID()).name("Ana Ruiz").document("1087")
                .riskScore(BigDecimal.valueOf(84.5)).triageLevel(TriageLevel.TRIAGE_I_ROJO).status(CareStatus.EN_ESPERA)
                .evaluatedAt(OffsetDateTime.parse("2026-10-28T10:00:00-05:00"))
                .heartRate(120).spo2(90).systolic(90).diastolic(60).temperature(BigDecimal.valueOf(38.5)).respiratoryRate(26)
                .glucose(45).painScale(7).consciousness("C")
                .immediateActions(List.of("Dextrosa IV")).testsToOrder(List.of("Glucometría control"))
                .monitoring(List.of("Glucosa cada 15 min")).redFlags(List.of("Convulsión"))
                .symptomExplanation("Hipoglucemia grave").photoFindings("Sin lesiones")
                .destinationArea("Reanimación (sala de choque)").destinationCode("Código azul")
                .destinationReason("Nivel I").maxWaitMinutes(0).estimatedFields(List.of("temperature"))
                .build();

        RankedPatientDTO back = RankedPatientMessages.toDto(RankedPatientMessages.toProto(original));

        assertThat(back.getGlucose()).isEqualTo(45);
        assertThat(back.getPainScale()).isEqualTo(7);
        assertThat(back.getConsciousness()).isEqualTo("C");
        assertThat(back.getImmediateActions()).containsExactly("Dextrosa IV");
        assertThat(back.getTestsToOrder()).containsExactly("Glucometría control");
        assertThat(back.getMonitoring()).containsExactly("Glucosa cada 15 min");
        assertThat(back.getRedFlags()).containsExactly("Convulsión");
        assertThat(back.getSymptomExplanation()).isEqualTo("Hipoglucemia grave");
        assertThat(back.getPhotoFindings()).isEqualTo("Sin lesiones");
        assertThat(back.getDestinationArea()).isEqualTo("Reanimación (sala de choque)");
        assertThat(back.getDestinationCode()).isEqualTo("Código azul");
        assertThat(back.getMaxWaitMinutes()).isZero();
        assertThat(back.getEstimatedFields()).containsExactly("temperature");
        assertThat(back.getRiskScore()).isEqualByComparingTo(BigDecimal.valueOf(84.5));
    }

    @Test
    @DisplayName("GM-04: La respuesta de la IA conserva recomendaciones y área sugerida")
    void gm04_assistIdaYVuelta() {
        AiAssistDTO original = AiAssistDTO.builder()
                .active(true).suggestedScore(80.0).dangerLead("SCA")
                .immediateActions(List.of("ECG")).testsToOrder(List.of("Troponina"))
                .monitoring(List.of("Monitor")).redFlags(List.of("Hipotensión"))
                .symptomExplanation("Dolor típico").suggestedArea("URGENCIAS_CRITICAS").photoFindings("Palidez")
                .build();

        AiAssistDTO back = AssistMessages.toDto(AssistMessages.toReply(original));

        assertThat(back.isActive()).isTrue();
        assertThat(back.getSuggestedScore()).isEqualTo(80.0);
        assertThat(back.getImmediateActions()).containsExactly("ECG");
        assertThat(back.getTestsToOrder()).containsExactly("Troponina");
        assertThat(back.getSuggestedArea()).isEqualTo("URGENCIAS_CRITICAS");
        assertThat(back.getPhotoFindings()).isEqualTo("Palidez");
    }
}
