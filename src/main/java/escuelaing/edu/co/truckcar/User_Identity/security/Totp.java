package escuelaing.edu.co.truckcar.User_Identity.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * TOTP (RFC 6238) con HMAC-SHA1, pasos de 30 s y 6 dígitos: lo que esperan Google Authenticator, Authy y Microsoft
 * Authenticator. El secreto viaja en Base32 (RFC 4648, sin relleno), como en la URI otpauth://.
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    private static final int SECRET_BYTES = 20; // 160 bits, el tamaño de HMAC-SHA1 (RFC 4226 §4)
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** Secreto nuevo en Base32. */
    public static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static long stepAt(long epochMillis) {
        return Math.floorDiv(epochMillis / 1000, STEP_SECONDS);
    }

    /** Código del paso indicado, con ceros a la izquierda. */
    public static String code(String base32Secret, long step) {
        return code(base32Decode(base32Secret), step, DIGITS);
    }

    static String code(byte[] key, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format(Locale.ROOT, "%0" + digits + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 no disponible", e);
        }
    }

    /**
     * Paso con el que coincide el código, mirando el actual y ±window pasos (desfase del reloj del teléfono), o -1. Un
     * paso menor o igual que {@code lastUsedStep} no se acepta: el mismo código no sirve dos veces.
     */
    public static long matchStep(String base32Secret, String code, long epochMillis, int window, Long lastUsedStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        byte[] key = base32Decode(base32Secret);
        long now = stepAt(epochMillis);
        for (long step = now - window; step <= now + window; step++) {
            boolean equal = MessageDigest.isEqual(code(key, step, DIGITS).getBytes(), code.getBytes());
            if (equal && (lastUsedStep == null || step > lastUsedStep)) {
                return step;
            }
        }
        return -1;
    }

    public static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return out.toString();
    }

    public static byte[] base32Decode(String text) {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Secreto Base32 inválido");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }
}
