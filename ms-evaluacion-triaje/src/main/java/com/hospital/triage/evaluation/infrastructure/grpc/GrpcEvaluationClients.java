package com.hospital.triage.evaluation.infrastructure.grpc;

import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.exception.PatientNotFoundException;
import com.hospital.triage.evaluation.application.port.AssistInput;
import com.hospital.triage.evaluation.application.port.ClinicalAiPort;
import com.hospital.triage.grpc.mapping.AssistMessages;
import com.google.protobuf.ByteString;
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

import java.util.Base64;
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
            PatientView view = patients.withDeadlineAfter(25, TimeUnit.SECONDS)
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
            PatientList list = patients.withDeadlineAfter(25, TimeUnit.SECONDS).getPatients(request.build());
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
    public AiAssistDTO assist(AssistInput input) {
        AssistRequest request = toRequest(input);
        try {
            AssistReply reply = ia.withDeadlineAfter(50, TimeUnit.SECONDS).assist(request);
            return AssistMessages.toDto(reply);
        } catch (StatusRuntimeException ex) {
            log.warn("ms-ia-recomendacion no respondió, se usa solo el protocolo: {}", ex.getStatus().getCode());
            return AiAssistDTO.builder().active(false).protocolScore(input.protocolScore()).build();
        }
    }

    static AssistRequest toRequest(AssistInput input) {
        AssistRequest.Builder request = AssistRequest.newBuilder()
                .setConditionId(text(input.conditionId()))
                .setReason(text(input.reason()))
                .setHistory(text(input.history()))
                .setAge(input.age())
                .setSex(text(input.sex()))
                .setHeartRate(input.heartRate())
                .setSpo2(input.spo2())
                .setSystolic(input.systolic())
                .setDiastolic(input.diastolic())
                .setTemperature(input.temperature() == null ? 0 : input.temperature().doubleValue())
                .setRespiratoryRate(input.respiratoryRate())
                .setConsciousness(text(input.consciousness()))
                .setProtocolScore(input.protocolScore())
                .setNews2(input.news2());
        if (input.localModelScore() != null) {
            request.setLocalModelScore(input.localModelScore());
        }
        if (input.glucose() != null) {
            request.setGlucose(input.glucose());
        }
        if (input.painScale() != null) {
            request.setPainScale(input.painScale());
        }
        if (input.estimatedFields() != null) {
            request.addAllEstimatedFields(input.estimatedFields());
        }
        request.setPregnant(input.pregnant());
        if (input.gestationWeeks() != null) {
            request.setGestationWeeks(input.gestationWeeks());
        }
        byte[] image = decodePhoto(input.photoBase64());
        if (image.length > 0) {
            request.setImage(ByteString.copyFrom(image))
                    .setImageMime(input.photoMime() == null || input.photoMime().isBlank() ? "image/jpeg" : input.photoMime());
        }
        return request.build();
    }

    static byte[] decodePhoto(String raw) {
        if (raw == null || raw.isBlank()) {
            return new byte[0];
        }
        int comma = raw.indexOf(',');
        String data = raw.startsWith("data:") && comma > 0 ? raw.substring(comma + 1) : raw;
        try {
            return Base64.getMimeDecoder().decode(data.trim());
        } catch (IllegalArgumentException ex) {
            return new byte[0];
        }
    }

    @Override
    public boolean configured() {
        try {
            return ia.withDeadlineAfter(5, TimeUnit.SECONDS)
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
            SymptomSeverityReply reply = ia.withDeadlineAfter(25, TimeUnit.SECONDS)
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
                view.getHistory(),
                emptyToNull(view.getPhone()),
                view.getPregnant(),
                view.hasGestationWeeks() ? view.getGestationWeeks() : null
        );
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
