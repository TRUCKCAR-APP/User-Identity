-- ═══════════════════════════════════════════════════════════════════
-- V1 — Esquema inicial: tablas users y refresh_tokens
-- TruckCar User-Identity Microservice
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE users (
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

CREATE INDEX idx_users_email  ON users (email);
CREATE INDEX idx_users_role   ON users (role);
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE refresh_tokens (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255)    NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ     NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens (token);

INSERT INTO users (id, email, password, full_name, role, status, created_at, updated_at)
VALUES (
           gen_random_uuid(),
           'admin@truckcar.com',
           '$2a$10$wK1k6Iq5x2S9VqA9y/zB.eZ2Q5v7Z0B7E0O3Y1l1N4Y5g2c3E5k2G',
           'Admin TruckCar',
           'ADMIN',
           'ACTIVE',
           CURRENT_TIMESTAMP,
           CURRENT_TIMESTAMP
       ) ON CONFLICT (email) DO NOTHING;