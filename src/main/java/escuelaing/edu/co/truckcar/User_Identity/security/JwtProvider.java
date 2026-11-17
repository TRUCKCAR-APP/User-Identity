package escuelaing.edu.co.truckcar.User_Identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Componente encargado de generar y validar JSON Web Tokens.
 */
@Component
public class JwtProvider {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    /** Alcance del token temporal entre la contraseña y el código 2FA. */
    public static final String TWO_FACTOR_PENDING_SCOPE = "2FA_PENDING";

    /**
     * Llave del token temporal del 2FA, derivada del secreto JWT: firma distinta a la de los access tokens, así que el
     * api-gateway, Notifications y el propio filtro de este servicio lo rechazan como credencial.
     */
    private SecretKey pendingKey;
    private long pendingTokenTtlMs = 300_000;

    public JwtProvider(String secret, long accessTokenExpirationMs, long refreshTokenExpirationMs) {
        this(secret, accessTokenExpirationMs, refreshTokenExpirationMs, 300_000);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JwtProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs,
            @Value("${app.two-factor.pending-token-ttl-ms:300000}") long pendingTokenTtlMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Defina TRUCKCAR_JWT_SECRET: secreto JWT en Base64 (>= 256 bits) compartido con api-gateway y Notifications");
        }
        try {
            this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "TRUCKCAR_JWT_SECRET debe ser un valor Base64 de al menos 256 bits", e);
        }
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.pendingTokenTtlMs = pendingTokenTtlMs;
        try {
            byte[] base = Decoders.BASE64.decode(secret);
            java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
            sha.update(base);
            sha.update("truckcar-2fa-pending-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            this.pendingKey = Keys.hmacShaKeyFor(sha.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Token temporal (5 min) que solo sirve en POST /api/v1/auth/2fa/verify-login: sin rol y con scope 2FA_PENDING. */
    public String generateTwoFactorPendingToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("scope", TWO_FACTOR_PENDING_SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(pendingTokenTtlMs)))
                .signWith(pendingKey)
                .compact();
    }

    /** Usuario del token temporal, o vacío si no es uno válido, vigente y con scope 2FA_PENDING. */
    public java.util.Optional<UUID> parseTwoFactorPendingToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(pendingKey).build().parseSignedClaims(token).getPayload();
            if (!TWO_FACTOR_PENDING_SCOPE.equals(claims.get("scope", String.class)) || claims.get("role") != null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    public long getPendingTokenTtlMs() {
        return pendingTokenTtlMs;
    }

    /**
     * Genera un access token JWT para el usuario indicado.
     */
    public String generateAccessToken(UUID userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of(
                        "email", email,
                        "role", role
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpirationMs)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Genera un refresh token opaco (UUID), cuyo hash se persiste en BD.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Extrae el subject (userId) de un access token válido.
     */
    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(extractClaims(token).getSubject());
    }

    /**
     * Extrae el email del access token.
     */
    public String getEmailFromToken(String token) {
        return extractClaims(token).get("email", String.class);
    }

    /**
     * Extrae el rol del access token.
     */
    public String getRoleFromToken(String token) {
        return extractClaims(token).get("role", String.class);
    }

    /**
     * Valida la firma y expiración del token.
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = extractClaims(token);
            // Un access token siempre tiene rol y nunca el scope del segundo paso (defensa en profundidad: el token
            // temporal ya falla la firma con esta llave).
            if (claims.get("role") == null || claims.get("scope") != null) {
                return false;
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
