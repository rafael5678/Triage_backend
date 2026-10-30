# ADR-003: Comunicación Interna mediante gRPC y Protocol Buffers

## Estado
Aceptado

## Contexto
Los microservicios necesitan sincronizar información clínica en tiempo real (por ejemplo, el cálculo de triaje requiere consultar antecedentes del paciente y pedir sugerencias a la IA). El protocolo HTTP/1.1 REST introduce sobrecarga de serialización JSON, consumo de ancho de banda y falta de contratos fuertemente tipados.

## Decisión
Se adopta **gRPC** sobre **Protocol Buffers v3** para toda la comunicación interna entre microservicios dentro de la red privada:
- Definición de contratos centralizada en `triage-grpc-contracts/src/main/proto/triage.proto`.
- Servicios: `PatientLookup`, `ClinicalAi`, `EvaluationAccess`.
- Transporte: HTTP/2 con soporte para multiplexación de peticiones y baja latencia.

## Consecuencias
- **Positivas:** Comunicación binaria ultrarrápida, validación en tiempo de compilación y stubs generados automáticamente.
- **Negativas:** Los navegadores web no pueden invocar gRPC directamente sin un proxy (lo cual refuerza el uso de REST en el API Gateway para el frontend).

