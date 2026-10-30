# Modelo de Datos y Esquema Relacional PostgreSQL — TriageIA

## 1. Instancia y Propiedad de Datos
Base de datos: `hospital_triaje` (PostgreSQL 16).
La arquitectura utiliza un modelo de persistencia compartida con segregación de propiedad estricta por microservicio:

| Microservicio | Tablas Propiedad Exclusiva | Propósito |
|---|---|---|
| `ms-pacientes-auth` | `pacientes`, `app_users` | Datos demográficos y personal autorizado. |
| `ms-evaluacion-triaje` | `signos_vitales`, `evaluaciones_triage` | Constantes fisiológicas y clasificaciones. |
| `ms-ia-recomendacion` | `symptoms`, `uso_gemini` | Catálogo de síntomas y control de cuota de IA. |
| `ms-cola-espera` | *(Ninguna)* | Servicio orquestador en memoria. |

---

## 2. Diagrama Entidad-Relación Lógico

```mermaid
erDiagram
    APP_USERS {
        uuid id PK
        varchar username UK
        varchar password_hash
        varchar display_name
        varchar role
        boolean active
        timestamptz created_at
    }

    PACIENTES {
        uuid id PK
        varchar nombre
        varchar documento UK
        integer edad
        varchar sexo
        varchar lesion_id
        varchar motivo
        varchar antecedentes
        varchar telefono
        boolean embarazo
        integer semanas_gestacion
        timestamptz creado_en
    }

    SIGNOS_VITALES {
        uuid id PK
        uuid paciente_id FK
        integer frecuencia_cardiaca
        integer spo2
        integer pas
        integer pad
        numeric temperatura
        integer frecuencia_respiratoria
        integer glucosa
        integer dolor
        varchar conciencia
        timestamptz registrado_en
    }

    EVALUACIONES_TRIAGE {
        uuid id PK
        uuid paciente_id FK
        uuid signos_id FK
        numeric score_riesgo
        varchar nivel_triage
        varchar estado
        integer posicion_cola
        integer news2
        varchar area_destino
        varchar codigo_destino
        boolean vigente
        timestamptz evaluado_en
        timestamptz llegada_en
        timestamptz atencion_inicio
    }

    SYMPTOMS {
        varchar id PK
        varchar label
        varchar hint
        integer severity
        boolean active
    }

    USO_GEMINI {
        date dia PK
        integer llamadas
    }

    PACIENTES ||--o{ SIGNOS_VITALES : "posee mediciones"
    PACIENTES ||--o{ EVALUACIONES_TRIAGE : "posee evoluciones"
    SIGNOS_VITALES ||--o| EVALUACIONES_TRIAGE : "asociada a"
```

---

## 3. Secuencia de Scripts SQL de Inicialización
En el proyecto `Triage_base-de-datos/sql/`:
1. `01_schema.sql`: Estructura base de pacientes, signos vitales y evaluaciones.
2. `02_catalogo.sql`: Referencia de catálogo legacy.
3. `03_triaje_ampliado.sql`: Ajustes de signos vitales (glucosa, dolor, conciencia AVPU) y destinos.
4. `04_historial_avisos.sql`: Soporte para reevaluaciones, llamadas a Gemini y campos obstétricos.
5. `05_usuarios_y_sintomas.sql`: DDL formal para `app_users` y catálogo unificado de `symptoms`.

