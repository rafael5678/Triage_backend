package com.hospital.triage.queue.infrastructure.grpc;

import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.exception.PatientNotFoundException;
import com.hospital.triage.grpc.mapping.RankedPatientMessages;
import com.hospital.triage.grpc.v1.CareStatusUpdate;
import com.hospital.triage.grpc.v1.EvaluationAccessGrpc;
import com.hospital.triage.grpc.v1.NotifyMark;
import com.hospital.triage.grpc.v1.PresenceUpdate;
import com.hospital.triage.grpc.v1.PositionBatch;
import com.hospital.triage.grpc.v1.QueuePosition;
import com.hospital.triage.grpc.v1.RankedPatientList;
import com.hospital.triage.grpc.v1.StatusQuery;
import com.google.protobuf.Empty;
import com.hospital.triage.queue.application.port.EvaluationPort;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class GrpcEvaluationAdapter implements EvaluationPort {

    @GrpcClient("evaluacion")
    private EvaluationAccessGrpc.EvaluationAccessBlockingStub evaluations;

    @Override
    public List<RankedPatientDTO> listByStatus(CareStatus status) {
        try {
            RankedPatientList reply = evaluations.withDeadlineAfter(45, TimeUnit.SECONDS)
                    .listByStatus(StatusQuery.newBuilder().setStatus(status.name()).build());
            return reply.getPatientsList().stream().map(RankedPatientMessages::toDto).toList();
        } catch (StatusRuntimeException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public List<RankedPatientDTO> listAll() {
        try {
            RankedPatientList reply = evaluations.withDeadlineAfter(45, TimeUnit.SECONDS)
                    .listAll(Empty.getDefaultInstance());
            return reply.getPatientsList().stream().map(RankedPatientMessages::toDto).toList();
        } catch (StatusRuntimeException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public RankedPatientDTO updateStatus(UUID patientId, CareStatus status) {
        try {
            var reply = evaluations.withDeadlineAfter(45, TimeUnit.SECONDS).updateCareStatus(
                    CareStatusUpdate.newBuilder()
                            .setPatientId(patientId.toString())
                            .setStatus(status.name())
                            .build());
            return RankedPatientMessages.toDto(reply);
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new PatientNotFoundException(patientId.toString());
            }
            throw unavailable(ex);
        }
    }

    @Override
    public void applyPositions(List<RankedPatientDTO> ranked) {
        PositionBatch.Builder batch = PositionBatch.newBuilder();
        for (RankedPatientDTO row : ranked) {
            if (row.getPatientId() == null || row.getQueuePosition() == null) {
                continue;
            }
            batch.addPositions(QueuePosition.newBuilder()
                    .setPatientId(row.getPatientId().toString())
                    .setPosition(row.getQueuePosition())
                    .build());
        }
        try {
            evaluations.withDeadlineAfter(45, TimeUnit.SECONDS).applyPositions(batch.build());
        } catch (StatusRuntimeException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public RankedPatientDTO markNotified(UUID patientId, String channel) {
        return call(patientId, () -> evaluations.withDeadlineAfter(45, TimeUnit.SECONDS).markNotified(
                NotifyMark.newBuilder().setPatientId(patientId.toString()).setChannel(channel == null ? "" : channel).build()));
    }

    @Override
    public RankedPatientDTO setPresence(UUID patientId, boolean present) {
        return call(patientId, () -> evaluations.withDeadlineAfter(45, TimeUnit.SECONDS).setPresence(
                PresenceUpdate.newBuilder().setPatientId(patientId.toString()).setPresent(present).build()));
    }

    private RankedPatientDTO call(UUID patientId, java.util.function.Supplier<com.hospital.triage.grpc.v1.RankedPatient> rpc) {
        try {
            return RankedPatientMessages.toDto(rpc.get());
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new PatientNotFoundException(patientId.toString());
            }
            throw unavailable(ex);
        }
    }

    private RuntimeException unavailable(StatusRuntimeException ex) {
        return new IllegalStateException("No hay conexión con ms-evaluacion-triaje");
    }
}
