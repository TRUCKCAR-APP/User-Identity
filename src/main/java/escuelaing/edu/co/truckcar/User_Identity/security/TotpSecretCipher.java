package escuelaing.edu.co.truckcar.User_Identity.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra el secreto TOTP en la base (AES-256-GCM, IV aleatorio por valor). Quien lea la tabla users no puede generar
 * códigos. La llave es TRUCKCAR_TOTP_ENCRYPTION_KEY o, si no se define, una derivada del secreto JWT (SHA-256 con
 * un prefijo propio). Si se cambia la llave, los secretos guardados dejan de descifrarse y esos usuarios deben volver a
 * configurar el 2FA.
 */
@Component
public class TotpSecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public TotpSecretCipher(@Value("${app.two-factor.encryption-key:}") String encryptionKey,
                            @Value("${app.jwt.secret}") String jwtSecret) {
        String material = encryptionKey != null && !encryptionKey.isBlank() ? encryptionKey : jwtSecret;
        if (material == null || material.isBlank()) {
            throw new IllegalStateException("Defina TRUCKCAR_TOTP_ENCRYPTION_KEY o TRUCKCAR_JWT_SECRET");
        }
        this.key = new SecretKeySpec(sha256("truckcar-totp-secret-v1:" + material), "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto TOTP", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Secreto TOTP con un formato desconocido");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar el secreto TOTP (¿cambió la llave?)", e);
        }
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
