# Interoperabilidad Hospitalaria (HL7 / FHIR)

El modelo de datos y DTOs están alineados con el recurso `Patient` y `Observation` del estándar FHIR:

- Mapeo directo de `frecuencia_cardiaca` al código LOINC `8867-4`.
- Mapeo de `presion_sistolica` al código LOINC `8480-6`.
- Facilita la futura integración con sistemas de historia clínica electrónica (HCE).
