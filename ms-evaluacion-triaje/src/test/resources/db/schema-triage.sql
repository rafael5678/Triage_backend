-- Copia de las tablas de Triage_base-de-datos/sql (01_schema + 03 + 04) que usa evaluación.
-- Las pruebas de integración validan las entidades JPA contra este esquema (ddl-auto=validate):
-- si una entidad apunta a una columna que no existe, la prueba falla antes de llegar a Supabase.

CREATE TABLE pacientes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre          VARCHAR(160) NOT NULL,
    documento       VARCHAR(40)  NOT NULL UNIQUE,
    edad            INTEGER      NOT NULL CHECK (edad BETWEEN 0 AND 120),
    sexo            VARCHAR(20),
    lesion_id       VARCHAR(40),
    motivo          VARCHAR(500),
    antecedentes    VARCHAR(400),
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    telefono        VARCHAR(20),
    embarazo        BOOLEAN      NOT NULL DEFAULT FALSE,
    semanas_gestacion INTEGER
);

CREATE TABLE signos_vitales (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id              UUID NOT NULL REFERENCES pacientes(id) ON DELETE CASCADE,
    frecuencia_cardiaca      INTEGER NOT NULL CHECK (frecuencia_cardiaca BETWEEN 20 AND 250),
    spo2                     INTEGER NOT NULL CHECK (spo2 BETWEEN 0 AND 100),
    pas                      INTEGER NOT NULL CHECK (pas BETWEEN 50 AND 260),
    pad                      INTEGER NOT NULL CHECK (pad BETWEEN 20 AND 160),
    temperatura              NUMERIC(4,1) NOT NULL CHECK (temperatura BETWEEN 30 AND 43),
    frecuencia_respiratoria  INTEGER NOT NULL CHECK (frecuencia_respiratoria BETWEEN 4 AND 60),
    registrado_en            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    glucosa                  INTEGER CHECK (glucosa BETWEEN 10 AND 1500),
    dolor                    INTEGER CHECK (dolor BETWEEN 0 AND 10),
    conciencia               VARCHAR(1) CHECK (conciencia IN ('A', 'C', 'V', 'P', 'U'))
);

CREATE TABLE evaluaciones_triage (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id     UUID NOT NULL REFERENCES pacientes(id) ON DELETE CASCADE,
    signos_id       UUID REFERENCES signos_vitales(id),
    score_riesgo    NUMERIC(5,1) NOT NULL CHECK (score_riesgo BETWEEN 0 AND 100),
    nivel_triage    VARCHAR(32) NOT NULL,
    estado          VARCHAR(24) NOT NULL DEFAULT 'EN_ESPERA',
    posicion_cola   INTEGER,
    news2           INTEGER,
    evaluado_en     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fuente_score    VARCHAR(24),
    explicacion_ia  TEXT,
    area_destino    VARCHAR(40),
    codigo_destino  VARCHAR(60),
    motivo_destino  VARCHAR(300),
    espera_max_min  INTEGER,
    campos_estimados VARCHAR(200),
    vigente         BOOLEAN      NOT NULL DEFAULT TRUE,
    evaluado_por    VARCHAR(120),
    llegada_en      TIMESTAMPTZ,
    atencion_inicio TIMESTAMPTZ,
    presente        BOOLEAN      NOT NULL DEFAULT TRUE,
    notificado_en   TIMESTAMPTZ,
    canal_aviso     VARCHAR(20)
);
