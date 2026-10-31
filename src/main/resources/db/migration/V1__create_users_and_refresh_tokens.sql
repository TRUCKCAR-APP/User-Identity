-- ═══════════════════════════════════════════════════════════════════
-- V1 — Esquema inicial: tablas users y refresh_tokens
-- TruckCar User-Identity Microservice. Idempotente: en bases existentes db-init ya movió estas tablas al
-- esquema user_identity y aquí no cambia nada.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS users (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100)    NOT NULL,
    phone_number    VARCHAR(20),
    document_type   VARCHAR(20),
    document_number VARCHAR(50),
    role            VARCHAR(20)     NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_users_email  ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_role   ON users (role);
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255)    NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ     NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token   ON refresh_tokens (token);

-- El ADMIN inicial no se siembra aquí (sin credenciales en el repositorio): lo crea AdminBootstrap al arrancar
-- con TRUCKCAR_ADMIN_EMAIL / TRUCKCAR_ADMIN_PASSWORD.