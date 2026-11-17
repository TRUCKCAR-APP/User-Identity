-- V4: empresas transportadoras y afiliación de conductores.
--  - Rol TRANSPORTADORA (empresa que registra y gestiona a sus conductores).
--  - companies: la crea el usuario TRANSPORTADORA al registrarse (owner_user_id).
--  - users.driver_type: INDEPENDIENTE (solo navegación, sin cargas) o EMPRESA (recibe cargas con la afiliación ACTIVA).
--    Las cuentas existentes quedan con NULL y conservan el comportamiento anterior.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('CONDUCTOR', 'COORDINADOR', 'GENERADOR', 'TRANSPORTADORA', 'ADMIN'));

CREATE TABLE IF NOT EXISTS companies (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(150)  NOT NULL,
    nit           VARCHAR(20)   NOT NULL UNIQUE,
    phone_number  VARCHAR(20),
    owner_user_id UUID          NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS driver_type        VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_id         UUID REFERENCES companies(id);
ALTER TABLE users ADD COLUMN IF NOT EXISTS affiliation_status VARCHAR(20);

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_driver_type_check;
ALTER TABLE users ADD CONSTRAINT users_driver_type_check
    CHECK (driver_type IS NULL OR driver_type IN ('INDEPENDIENTE', 'EMPRESA'));
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_affiliation_status_check;
ALTER TABLE users ADD CONSTRAINT users_affiliation_status_check
    CHECK (affiliation_status IS NULL OR affiliation_status IN ('PENDIENTE', 'ACTIVA', 'RECHAZADA'));
-- Un independiente no tiene empresa ni estado de afiliación; un conductor de empresa siempre tiene ambos.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_affiliation_consistency_check;
ALTER TABLE users ADD CONSTRAINT users_affiliation_consistency_check
    CHECK ((driver_type IS DISTINCT FROM 'EMPRESA' AND company_id IS NULL AND affiliation_status IS NULL)
        OR (driver_type = 'EMPRESA' AND company_id IS NOT NULL AND affiliation_status IS NOT NULL));

CREATE INDEX IF NOT EXISTS idx_users_company_id ON users (company_id);
