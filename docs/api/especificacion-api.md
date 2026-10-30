# Especificación de la API REST Unificada — TriageIA

Todas las solicitudes externas de clientes deben ingresar a través del **API Gateway** en el puerto `8080` (o el dominio público en Azure: `https://<gateway-fqdn>.azurecontainerapps.io`).

---

## 1. Módulo de Autenticación y Personal (`ms-pacientes-auth`)

### `POST /api/v1/auth/login`
- **Descripción:** Valida credenciales de acceso hospitalario.
- **Body:**
  ```json
  {
    "username": "admin",
    "password": "UrgenciasAdmin26"
  }
  ```
- **Respuesta Exitosa (200 OK):**
  ```json
  {
    "id": "7f1c2a4e-1234-4000-8000-000000000001",
    "username": "admin",
    "displayName": "Administrador",
    "role": "ADMIN",
    "active": true
  }
  ```

### `POST /api/v1/triage/patients`
- **Descripción:** Registra un nuevo paciente para atención médica.
- **Body:**
  ```json
  {
    "name": "María González",
    "document": "1098765432",
    "age": 28,
    "sex": "F",
    "conditionId": "cefalea",
    "reason": "Dolor de cabeza intenso y visión borrosa",
    "history": "Hipertensión gestacional",
    "phone": "573001234567",
    "pregnant": true,
    "gestationWeeks": 32
  }
  ```

---

## 2. Módulo de Evaluación Clínica (`ms-evaluacion-triaje`)

### `POST /api/v1/triage/evaluations`
- **Descripción:** Registra signos vitales y calcula el nivel de gravedad NEWS2 / Protocolo.
- **Body:**
  ```json
  {
    "patientId": "7f1c2a4e-1234-4000-8000-000000000001",
    "heartRate": 98,
    "spo2": 97,
    "systolic": 165,
    "diastolic": 112,
    "temperature": 37.0,
    "respiratoryRate": 20,
    "glucose": 95,
    "painScale": 8,
    "consciousness": "A",
    "evaluatedBy": "Dra. López"
  }
  ```

### `POST /api/v1/triage/analyze`
- **Descripción:** Realiza un cálculo instantáneo en memoria sin persistir inmediatamente.

---

## 3. Módulo de Sala de Espera y Turnos (`ms-cola-espera`)

### `GET /api/v1/triage/patients`
- **Descripción:** Retorna la lista priorizada de pacientes en sala de espera.

### `GET /api/v1/triage/events` (Server-Sent Events)
- **Descripción:** Stream de eventos en vivo para clientes reactivos. Emite eventos `hola` al conectar y `cambio` cuando hay modificaciones en la sala.

### `PATCH /api/v1/triage/care/{id}`
- **Descripción:** Cambia el estado a `EN_ATENCION`.

### `PATCH /api/v1/triage/care/{id}/status?status=ATENDIDO`
- **Descripción:** Finaliza el ciclo de triaje del paciente.

---

## 4. Módulo de Inteligencia Artificial (`ms-ia-recomendacion`)

### `POST /api/v1/ia/intake`
- **Descripción:** Extrae datos estructurados desde el relato libre del paciente.

### `GET /api/v1/catalog/symptoms`
- **Descripción:** Retorna el catálogo clínico de síntomas y su severidad base.

