package com.hospital.triage.evaluation.infrastructure.grpc;

import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.exception.PatientNotFoundException;
import com.hospital.triage.evaluation.application.port.ClinicalAiPort;
import com.hospital.triage.evaluation.application.port.PatientLookupPort;
import com.hospital.triage.evaluation.application.port.PatientSnapshot;
import com.hospital.triage.evaluation.application.port.SymptomSeverityPort;
import com.hospital.triage.grpc.v1.AssistReply;
import com.hospital.triage.grpc.v1.AssistRequest;
import com.hospital.triage.grpc.v1.ClinicalAiGrpc;
import com.hospital.triage.grpc.v1.PatientId;
import com.hospital.triage.grpc.v1.PatientIds;
import com.hospital.triage.grpc.v1.PatientList;
import com.hospital.triage.grpc.v1.PatientLookupGrpc;
import com.hospital.triage.grpc.v1.PatientView;
import com.hospital.triage.grpc.v1.SymptomQuery;
import com.hospital.triage.grpc.v1.SymptomSeverityReply;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GrpcEvaluationClients implements PatientLookupPort, ClinicalAiPort, SymptomSeverityPort {

    @GrpcClient("pacientes")
    private PatientLookupGrpc.PatientLookupBlockingStub patients;

    @GrpcClient("ia")
    private ClinicalAiGrpc.ClinicalAiBlockingStub ia;

    @Override
    public PatientSnapshot require(UUID id) {
        try {
            PatientView view = patients.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getPatient(PatientId.newBuilder().setId(id.toString()).build());
            return toSnapshot(view);
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new PatientNotFoundException(id.toString());
            }
            throw new IllegalStateException("No hay conexión con ms-pacientes-auth");
        }
    }

    @Override
    public Map<UUID, PatientSnapshot> findAll(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        try {
            PatientIds.Builder request = PatientIds.newBuilder();
            ids.forEach(id -> request.addIds(id.toString()));
            PatientList list = patients.withDeadlineAfter(3, TimeUnit.SECONDS).getPatients(request.build());
            Map<UUID, PatientSnapshot> found = new HashMap<>();
            for (PatientView view : list.getPatientsList()) {
                PatientSnapshot snapshot = toSnapshot(view);
                if (snapshot.id() != null) {
                    found.put(snapshot.id(), snapshot);
                }
            }
            return found;
        } catch (StatusRuntimeException ex) {
            log.warn("No se pudieron leer pacientes por gRPC: {}", ex.getStatus().getCode());
            return Map.of();
        }
    }

    @Override
    public AiAssistDTO assist(String conditionId, String reason, String history, int age, String sex,
                              int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature,
                              int respiratoryRate, double protocolScore, int news2, Double localModelScore) {
        AssistRequest.Builder request = AssistRequest.newBuilder()
                .setConditionId(text(conditionId))
                .setReason(text(reason))
                .setHistory(text(history))
                .setAge(age)
                .setSex(text(sex))
                .setHeartRate(heartRate)
                .setSpo2(spo2)
                .setSystolic(systolic)
                .setDiastolic(diastolic)
                .setTemperature(temperature == null ? 0 : temperature.doubleValue())
                .setRespiratoryRate(respiratoryRate)
                .setProtocolScore(protocolScore)
                .setNews2(news2);
        if (localModelScore != null) {
            request.setLocalModelScore(localModelScore);
        }
        try {
            AssistReply reply = ia.withDeadlineAfter(15, TimeUnit.SECONDS).assist(request.build());
            return toAssist(reply);
        } catch (StatusRuntimeException ex) {
            log.warn("ms-ia-recomendacion no respondió, se usa solo el protocolo: {}", ex.getStatus().getCode());
            return AiAssistDTO.builder().active(false).protocolScore(protocolScore).build();
        }
    }

    @Override
    public boolean configured() {
        try {
            return ia.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .health(Empty.getDefaultInstance())
                    .getConfigured();
        } catch (StatusRuntimeException ex) {
            return false;
        }
    }

    @Override
    public Optional<Integer> severityOf(String conditionId) {
        if (conditionId == null || conditionId.isBlank()) {
            return Optional.empty();
        }
        try {
            SymptomSeverityReply reply = ia.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .symptomSeverity(SymptomQuery.newBuilder().setConditionId(conditionId).build());
            return reply.getFound() ? Optional.of(reply.getSeverity()) : Optional.empty();
        } catch (StatusRuntimeException ex) {
            log.warn("Severidad no disponible en ms-ia-recomendacion: {}", ex.getStatus().getCode());
            return Optional.empty();
        }
    }

    private PatientSnapshot toSnapshot(PatientView view) {
        UUID id;
        try {
            id = UUID.fromString(view.getId());
        } catch (IllegalArgumentException ex) {
            id = null;
        }
        return new PatientSnapshot(
                id,
                view.getName(),
                view.getDocument(),
                view.getAge(),
                emptyToNull(view.getSex()),
                emptyToNull(view.getConditionId()),
                view.getReason(),
                view.getHistory()
        );
    }

    private AiAssistDTO toAssist(AssistReply reply) {
        return AiAssistDTO.builder()
                .active(reply.getActive())
                .suggestedScore(reply.hasSuggestedScore() ? reply.getSuggestedScore() : null)
                .dangerLead(emptyToNull(reply.getDangerLead()))
                .meaning(emptyToNull(reply.getMeaning()))
                .reasons(new ArrayList<>(reply.getReasonsList()))
                .datasetReasons(new ArrayList<>(reply.getDatasetReasonsList()))
                .geminiReasons(new ArrayList<>(reply.getGeminiReasonsList()))
                .protocolScore(reply.hasProtocolScore() ? reply.getProtocolScore() : null)
                .localModelScore(reply.hasLocalModelScore() ? reply.getLocalModelScore() : null)
                .build();
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
