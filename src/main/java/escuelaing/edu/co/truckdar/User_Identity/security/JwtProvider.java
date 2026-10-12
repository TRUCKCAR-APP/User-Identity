package escuelaing.edu.co.truckdar.User_Identity.security;

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

    public JwtProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Defina TRUCKDAR_JWT_SECRET: secreto JWT en Base64 (>= 256 bits) compartido con api-gateway y Notifications");
        }
        try {
            this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "TRUCKDAR_JWT_SECRET debe ser un valor Base64 de al menos 256 bits", e);
        }
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
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
            extractClaims(token);
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
