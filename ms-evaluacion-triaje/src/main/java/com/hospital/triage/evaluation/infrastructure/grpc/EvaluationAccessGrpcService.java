package com.hospital.triage.evaluation.infrastructure.grpc;

import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.exception.PatientNotFoundException;
import com.hospital.triage.evaluation.application.service.EvaluationService;
import com.hospital.triage.grpc.mapping.RankedPatientMessages;
import com.hospital.triage.grpc.v1.CareStatusUpdate;
import com.hospital.triage.grpc.v1.EvaluationAccessGrpc;
import com.hospital.triage.grpc.v1.PositionBatch;
import com.hospital.triage.grpc.v1.RankedPatient;
import com.hospital.triage.grpc.v1.RankedPatientList;
import com.hospital.triage.grpc.v1.StatusQuery;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@GrpcService
public class EvaluationAccessGrpcService extends EvaluationAccessGrpc.EvaluationAccessImplBase {

    private final EvaluationService evaluations;

    public EvaluationAccessGrpcService(EvaluationService evaluations) {
        this.evaluations = evaluations;
    }

    @Override
    public void listByStatus(StatusQuery request, StreamObserver<RankedPatientList> responseObserver) {
        CareStatus status;
        try {
            status = CareStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Estado desconocido").asRuntimeException());
            return;
        }
        try {
            responseObserver.onNext(toList(evaluations.listByStatus(status)));
            responseObserver.onCompleted();
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listAll(Empty request, StreamObserver<RankedPatientList> responseObserver) {
        try {
            responseObserver.onNext(toList(evaluations.listAll()));
            responseObserver.onCompleted();
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void updateCareStatus(CareStatusUpdate request, StreamObserver<RankedPatient> responseObserver) {
        UUID patientId = parse(request.getPatientId(), responseObserver);
        if (patientId == null) {
            return;
        }
        CareStatus status;
        try {
            status = CareStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Estado desconocido").asRuntimeException());
            return;
        }
        try {
            responseObserver.onNext(RankedPatientMessages.toProto(evaluations.updateStatus(patientId, status)));
            responseObserver.onCompleted();
        } catch (PatientNotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void applyPositions(PositionBatch request, StreamObserver<Empty> responseObserver) {
        Map<UUID, Integer> positions = new LinkedHashMap<>();
        for (var position : request.getPositionsList()) {
            try {
                positions.put(UUID.fromString(position.getPatientId()), position.getPosition());
            } catch (IllegalArgumentException ignored) {
                // Se omite un id mal formado y se aplican el resto.
            }
        }
        try {
            evaluations.applyPositions(positions);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    private RankedPatientList toList(java.util.List<RankedPatientDTO> rows) {
        RankedPatientList.Builder builder = RankedPatientList.newBuilder();
        for (RankedPatientDTO row : rows) {
            builder.addPatients(RankedPatientMessages.toProto(row));
        }
        return builder.build();
    }

    private UUID parse(String raw, StreamObserver<?> observer) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("Id de paciente inválido").asRuntimeException());
            return null;
        }
    }
}
