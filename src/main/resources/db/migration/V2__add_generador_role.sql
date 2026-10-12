-- Rol GENERADOR (empresa generadora de carga).
-- La restricción users_role_check la generó Hibernate a partir del enum y ddl-auto=update no la actualiza al
-- agregar valores. Si la tabla aún no existe (primer arranque sobre una base compartida ya poblada), Hibernate la
-- creará después con la restricción completa a partir del enum actual.
DO $$
BEGIN
    IF to_regclass('users') IS NOT NULL THEN
        ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
        ALTER TABLE users ADD CONSTRAINT users_role_check
            CHECK (role IN ('CONDUCTOR', 'COORDINADOR', 'GENERADOR', 'ADMIN'));
    END IF;
END $$;
