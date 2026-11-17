-- V5: doble autenticación opcional (TOTP, RFC 6238). Desactivada en todas las cuentas existentes: inician sesión igual.
--  - two_factor_secret / two_factor_pending_secret: secreto TOTP cifrado con AES-GCM (nunca en claro). El pendiente
--    es el del QR mostrado en /2fa/setup hasta que /2fa/verify-setup lo confirme.
--  - two_factor_backup_codes: arreglo JSON de los SHA-256 (hex) de los códigos de respaldo que siguen sin usar.
--  - two_factor_last_step: último paso de 30 s aceptado; un código no se puede repetir (RFC 6238 §5.2).
--  - two_factor_failed_attempts / two_factor_locked_until: bloqueo temporal tras varios códigos inválidos.
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_secret          VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_pending_secret  VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_backup_codes    TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_last_step       BIGINT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_failed_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_locked_until    TIMESTAMPTZ;

-- Con el 2FA activo siempre hay un secreto confirmado.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_two_factor_secret_check;
ALTER TABLE users ADD CONSTRAINT users_two_factor_secret_check
    CHECK (two_factor_enabled = FALSE OR two_factor_secret IS NOT NULL);
