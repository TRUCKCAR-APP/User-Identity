-- V6: recuperación de contraseña con un código de 6 dígitos enviado al correo.
--  - code_hash: HMAC-SHA256 del código con una llave derivada del secreto JWT (el código nunca se guarda en claro y
--    no se puede probar fuera del servidor).
--  - expires_at: el código vale 10 minutos; attempts: códigos equivocados (a los 5 se invalida).
--  - reset_token_hash / token_expires_at: tras verificar el código se emite un token de un solo uso (SHA-256) con el
--    que se fija la contraseña nueva en los 10 minutos siguientes.
--  - consumed_at: usado, invalidado por uno nuevo o por exceso de intentos.
CREATE TABLE IF NOT EXISTS password_reset_codes (
    id                UUID PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash         VARCHAR(64)  NOT NULL,
    expires_at        TIMESTAMPTZ  NOT NULL,
    attempts          INTEGER      NOT NULL DEFAULT 0,
    reset_token_hash  VARCHAR(64),
    token_expires_at  TIMESTAMPTZ,
    consumed_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_user_created ON password_reset_codes (user_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_password_reset_token ON password_reset_codes (reset_token_hash)
    WHERE reset_token_hash IS NOT NULL;
