# TriageIA — gRPC Contracts (`triage-grpc-contracts`)

**Contratos binarios tipados con Protocol Buffers para la comunicación interna de alto rendimiento entre microservicios.**

---

## 1. Principio Arquitectónico
- Define la interfaz de comunicación síncrona entre microservicios dentro de la red interna del hospital / cluster.
- Generación automática de stubs de cliente y servidor en tiempo de compilación con `protobuf-maven-plugin`.
- Incluye utilidades de mapeo bidireccional (`AssistMessages`, `RankedPatientMessages`) para convertir tipos generados por Protobuf a los DTOs de `triage-common-contracts`.

---

## 2. Definición del Contrato (`src/main/proto/triage.proto`)

```protobuf
syntax = "proto3";
package hospital.triage.v1;

service PatientLookup {
  rpc GetPatient (PatientId) returns (PatientView);
  rpc GetPatients (PatientIds) returns (PatientList);
}

service ClinicalAi {
  rpc Assist (AssistRequest) returns (AssistReply);
  rpc Health (google.protobuf.Empty) returns (AiHealth);
  rpc SymptomSeverity (SymptomQuery) returns (SymptomSeverityReply);
}

service EvaluationAccess {
  rpc ListByStatus (StatusQuery) returns (RankedPatientList);
  rpc ListAll (google.protobuf.Empty) returns (RankedPatientList);
  rpc UpdateCareStatus (CareStatusUpdate) returns (RankedPatient);
  rpc ApplyPositions (PositionBatch) returns (google.protobuf.Empty);
  rpc MarkNotified (NotifyMark) returns (RankedPatient);
  rpc SetPresence (PresenceUpdate) returns (RankedPatient);
}
```

