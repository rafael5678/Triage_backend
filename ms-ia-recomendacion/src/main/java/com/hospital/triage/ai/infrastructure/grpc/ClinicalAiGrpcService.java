package com.hospital.triage.ai.infrastructure.grpc;

import com.hospital.triage.ai.application.service.ClinicalCase;
import com.hospital.triage.ai.application.service.GeminiTriageService;
import com.hospital.triage.ai.infrastructure.persistence.SymptomRepository;
import com.hospital.triage.grpc.mapping.AssistMessages;
import com.hospital.triage.grpc.v1.AiHealth;
import com.hospital.triage.grpc.v1.AssistReply;
import com.hospital.triage.grpc.v1.AssistRequest;
import com.hospital.triage.grpc.v1.ClinicalAiGrpc;
import com.hospital.triage.grpc.v1.SymptomQuery;
import com.hospital.triage.grpc.v1.SymptomSeverityReply;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;

@Component
public class ClinicalAiGrpcService extends ClinicalAiGrpc.ClinicalAiImplBase {

    private final GeminiTriageService gemini;
    private final SymptomRepository symptoms;

    public ClinicalAiGrpcService(GeminiTriageService gemini, SymptomRepository symptoms) {
        this.gemini = gemini;
        this.symptoms = symptoms;
    }

    @Override
    public void assist(AssistRequest request, StreamObserver<AssistReply> responseObserver) {
        ClinicalCase clinicalCase = ClinicalCase.builder()
                .conditionId(blankToNull(request.getConditionId()))
                .reason(request.getReason())
                .history(request.getHistory())
                .age(request.getAge())
                .sex(blankToNull(request.getSex()))
                .heartRate(request.getHeartRate())
                .spo2(request.getSpo2())
                .systolic(request.getSystolic())
                .diastolic(request.getDiastolic())
                .temperature(BigDecimal.valueOf(request.getTemperature()))
                .respiratoryRate(request.getRespiratoryRate())
                .glucose(request.hasGlucose() ? request.getGlucose() : null)
                .painScale(request.hasPainScale() ? request.getPainScale() : null)
                .consciousness(blankToNull(request.getConsciousness()))
                .estimatedFields(new ArrayList<>(request.getEstimatedFieldsList()))
                .photoBase64(request.getImage().isEmpty() ? null : Base64.getEncoder().encodeToString(request.getImage().toByteArray()))
                .photoMime(blankToNull(request.getImageMime()))
                .protocolScore(request.getProtocolScore())
                .news2(request.getNews2())
                .localModelScore(request.hasLocalModelScore() ? request.getLocalModelScore() : null)
                .build();
        responseObserver.onNext(AssistMessages.toReply(gemini.assist(clinicalCase)));
        responseObserver.onCompleted();
    }

    @Override
    public void health(Empty request, StreamObserver<AiHealth> responseObserver) {
        responseObserver.onNext(AiHealth.newBuilder().setConfigured(gemini.configured()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void symptomSeverity(SymptomQuery request, StreamObserver<SymptomSeverityReply> responseObserver) {
        var reply = symptoms.findById(request.getConditionId())
                .map(symptom -> SymptomSeverityReply.newBuilder()
                        .setFound(true)
                        .setSeverity(symptom.getSeverity())
                        .build())
                .orElseGet(() -> SymptomSeverityReply.newBuilder().setFound(false).build());
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
