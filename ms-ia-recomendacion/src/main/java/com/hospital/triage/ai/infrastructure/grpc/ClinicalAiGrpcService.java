package com.hospital.triage.ai.infrastructure.grpc;

import com.hospital.triage.ai.application.service.GeminiTriageService;
import com.hospital.triage.ai.infrastructure.persistence.SymptomRepository;
import com.hospital.triage.common.dto.AiAssistDTO;
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
        Double local = request.hasLocalModelScore() ? request.getLocalModelScore() : null;
        AiAssistDTO dto = gemini.assist(
                blankToNull(request.getConditionId()),
                request.getReason(),
                request.getHistory(),
                request.getAge(),
                blankToNull(request.getSex()),
                request.getHeartRate(),
                request.getSpo2(),
                request.getSystolic(),
                request.getDiastolic(),
                BigDecimal.valueOf(request.getTemperature()),
                request.getRespiratoryRate(),
                request.getProtocolScore(),
                request.getNews2(),
                local
        );
        responseObserver.onNext(toReply(dto));
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

    private AssistReply toReply(AiAssistDTO dto) {
        AssistReply.Builder builder = AssistReply.newBuilder()
                .setActive(dto.isActive())
                .setDangerLead(text(dto.getDangerLead()))
                .setMeaning(text(dto.getMeaning()));
        if (dto.getSuggestedScore() != null) {
            builder.setSuggestedScore(dto.getSuggestedScore());
        }
        if (dto.getProtocolScore() != null) {
            builder.setProtocolScore(dto.getProtocolScore());
        }
        if (dto.getLocalModelScore() != null) {
            builder.setLocalModelScore(dto.getLocalModelScore());
        }
        if (dto.getReasons() != null) {
            builder.addAllReasons(dto.getReasons());
        }
        if (dto.getDatasetReasons() != null) {
            builder.addAllDatasetReasons(dto.getDatasetReasons());
        }
        if (dto.getGeminiReasons() != null) {
            builder.addAllGeminiReasons(dto.getGeminiReasons());
        }
        return builder.build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
