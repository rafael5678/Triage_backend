# Contextos Delimitados (Bounded Contexts) — TriageIA

En concordancia con los principios de **Domain-Driven Design (DDD)**, el dominio de urgencias hospitalarias se divide en cuatro contextos delimitados y un gateway de integración.

---

## 1. Contexto de Pacientes y Autenticación (`ms-pacientes-auth`)
- **Dominio:** Ficha demográfica del paciente y credenciales del personal hospitalario.
- **Entidades:** `Patient`, `AppUser`.
- **Objetos de Valor:** `UserRole` (`ADMIN`, `MEDICO`, `ENFERMERO`, `RECEPCION`).
- **Límites de Contexto:** Es el único dueño de la identidad. Si el servicio de evaluación requiere conocer la edad, sexo o antecedentes de un paciente, debe invocar la interfaz gRPC `PatientLookup.GetPatient(id)`.

---

## 2. Contexto de Evaluación Clínica y Triaje (`ms-evaluacion-triaje`)
- **Dominio:** Captura de constantes fisiológicas, reglas clínicas objetivas (NEWS2, Glasgow, rangos pediátricos, alertas obstétricas) y asignación de prioridad de atención.
- **Entidades:** `VitalSigns`, `TriageEvaluation`.
- **Objetos de Valor:** `TriageLevel` (Rojo I, Naranja II, Amarillo III, Verde IV, Azul V), `CareArea` (Reanimación, Urgencias Críticas, Consulta Rápida, Sala de Curas, etc.).
- **Políticas Clínicas:**
  - Si un paciente presenta saturación $<85\%$ o hipotensión severa, se impone un piso de gravedad $\ge 86$ (Rojo I).
  - En pacientes pediátricos, los signos se evalúan contra las bandas de edad (`PediatricBand`).
  - En gestantes, presiones elevadas activan código preeclampsia y sangrado activa código rojo obstétrico.

---

## 3. Contexto de Cola de Espera y Turnos (`ms-cola-espera`)
- **Dominio:** Ordenamiento dinámico de la sala de espera, medición de tiempos de permanencia, percentiles y notificaciones en tiempo real.
- **Estado:** En memoria con sincronización persistente a través de `ms-evaluacion-triaje`.
- **Objetos de Valor:** `QueueStateDTO`, `CareStatus` (`EN_ESPERA`, `EN_ATENCION`, `ATENDIDO`, `CANCELADO`).
- **Límites de Contexto:** Orquesta la prioridad mediante la regla: primero mayor puntaje de riesgo; en caso de empate, menor tiempo de llegada.

---

## 4. Contexto de Recomendación e Inteligencia Artificial (`ms-ia-recomendacion`)
- **Dominio:** Catálogo de sintomatología clínica, ingesta de lenguaje natural de relatos y consulta contextual a modelos de lenguaje (Google Gemini).
- **Entidades:** `Symptom`, `GeminiUsage`.
- **Límites de Contexto:** Opera como un servicio de apoyo diagnóstico. La IA sugiere diagnósticos y áreas, pero nunca decide por encima del protocolo clínico normativo.

