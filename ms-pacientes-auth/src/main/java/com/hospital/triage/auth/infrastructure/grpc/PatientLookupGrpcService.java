package com.hospital.triage.auth.infrastructure.grpc;

import com.hospital.triage.auth.application.service.PatientService;
import com.hospital.triage.auth.domain.entity.Patient;
import com.hospital.triage.common.exception.PatientNotFoundException;
import com.hospital.triage.grpc.v1.PatientId;
import com.hospital.triage.grpc.v1.PatientIds;
import com.hospital.triage.grpc.v1.PatientList;
import com.hospital.triage.grpc.v1.PatientLookupGrpc;
import com.hospital.triage.grpc.v1.PatientView;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PatientLookupGrpcService extends PatientLookupGrpc.PatientLookupImplBase {

    private final PatientService patients;

    public PatientLookupGrpcService(PatientService patients) {
        this.patients = patients;
    }

    @Override
    public void getPatient(PatientId request, StreamObserver<PatientView> responseObserver) {
        UUID id = parse(request.getId(), responseObserver);
        if (id == null) {
            return;
        }
        try {
            responseObserver.onNext(toView(patients.findById(id)));
            responseObserver.onCompleted();
        } catch (PatientNotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getPatients(PatientIds request, StreamObserver<PatientList> responseObserver) {
        PatientList.Builder builder = PatientList.newBuilder();
        for (String raw : request.getIdsList()) {
            UUID id = quietParse(raw);
            if (id == null) {
                continue;
            }
            try {
                builder.addPatients(toView(patients.findById(id)));
            } catch (PatientNotFoundException ignored) {
                // Un id desconocido no tumba la consulta de la cola.
            }
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private UUID parse(String raw, StreamObserver<?> observer) {
        UUID id = quietParse(raw);
        if (id == null) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("Id de paciente inválido").asRuntimeException());
        }
        return id;
    }

    private UUID quietParse(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private PatientView toView(Patient patient) {
        return PatientView.newBuilder()
                .setId(patient.getId().toString())
                .setName(text(patient.getName()))
                .setDocument(text(patient.getDocument()))
                .setAge(patient.getAge() == null ? 0 : patient.getAge())
                .setSex(text(patient.getSex()))
                .setConditionId(text(patient.getConditionId()))
                .setReason(text(patient.getReason()))
                .setHistory(text(patient.getHistory()))
                .build();
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
