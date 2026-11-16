package escuelaing.edu.co.truckcar.User_Identity.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Los refresh tokens se guardan como SHA-256 en hexadecimal: quien lea la tabla refresh_tokens (un respaldo, una
 * consulta con permisos de lectura) no obtiene tokens que sirvan para abrir sesión. El token en claro solo existe en
 * la respuesta al cliente. Un hash rápido sin sal basta porque el token es aleatorio y largo (no se puede adivinar
 * por diccionario), a diferencia de una contraseña.
 */
public final class RefreshTokenHasher {

    private RefreshTokenHasher() {
    }

    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", e);
        }
    }
}
