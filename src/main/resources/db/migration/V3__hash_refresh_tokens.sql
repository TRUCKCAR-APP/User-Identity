-- Los refresh tokens se guardaban en claro aunque el modelo decía guardar el hash. Desde esta versión el servicio
-- guarda y busca SHA-256 en hexadecimal (RefreshTokenHasher), así que los tokens que ya existen se convierten aquí
-- con la misma función: las sesiones abiertas siguen valiendo después del despliegue.
-- sha256() es nativa de PostgreSQL 11+; encode(..., 'hex') da las mismas 64 letras minúsculas que HexFormat de Java.
UPDATE refresh_tokens
SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex')
WHERE token !~ '^[0-9a-f]{64}$';
