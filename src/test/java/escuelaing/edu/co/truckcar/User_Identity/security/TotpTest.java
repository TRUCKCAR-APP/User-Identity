package escuelaing.edu.co.truckcar.User_Identity.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TOTP (RFC 6238), Base32 (RFC 4648), cifrado del secreto y el token temporal del segundo paso. */
class TotpTest {

    private static final String JWT_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci10cnVja2Nhci10ZXN0cy0yNTZiaXRz";

    @Test
    void matchesTheRfc6238TestVectorsForSha1() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        // RFC 6238, Apéndice B (8 dígitos, pasos de 30 s).
        assertThat(Totp.code(key, 59 / 30, 8)).isEqualTo("94287082");
        assertThat(Totp.code(key, 1111111109L / 30, 8)).isEqualTo("07081804");
        assertThat(Totp.code(key, 1234567890L / 30, 8)).isEqualTo("89005924");
        assertThat(Totp.code(key, 2000000000L / 30, 8)).isEqualTo("69279037");
        // Las apps usan 6 dígitos: los 6 finales del mismo valor.
        assertThat(Totp.code(key, 59 / 30, 6)).isEqualTo("287082");
    }

    @Test
    void base32RoundTripsAndRejectsInvalidCharacters() {
        assertThat(Totp.base32Encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI"); // RFC 4648 §10
        assertThat(new String(Totp.base32Decode("mzxw6ytboi"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        String secret = Totp.newSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32Encode(Totp.base32Decode(secret))).isEqualTo(secret);
        assertThatThrownBy(() -> Totp.base32Decode("NO-ES-BASE32!")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsOneStepOfClockDriftAndNeverTheSameStepTwice() {
        String secret = Totp.newSecret();
        long now = 1_763_400_000_000L;
        long step = Totp.stepAt(now);
        assertThat(Totp.matchStep(secret, Totp.code(secret, step), now, 1, null)).isEqualTo(step);
        assertThat(Totp.matchStep(secret, Totp.code(secret, step - 1), now, 1, null)).isEqualTo(step - 1);
        assertThat(Totp.matchStep(secret, Totp.code(secret, step + 1), now, 1, null)).isEqualTo(step + 1);
        assertThat(Totp.matchStep(secret, Totp.code(secret, step - 2), now, 1, null)).as("fuera de la ventana").isEqualTo(-1);
        assertThat(Totp.matchStep(secret, Totp.code(secret, step), now, 1, step)).as("ya usado").isEqualTo(-1);
        assertThat(Totp.matchStep(secret, "12345", now, 1, null)).isEqualTo(-1);
        assertThat(Totp.matchStep(secret, "abcdef", now, 1, null)).isEqualTo(-1);
    }

    @Test
    void theSecretIsStoredEncryptedWithARandomIvAndTamperingIsDetected() {
        TotpSecretCipher cipher = new TotpSecretCipher("", JWT_SECRET);
        String secret = Totp.newSecret();
        String a = cipher.encrypt(secret);
        String b = cipher.encrypt(secret);
        assertThat(a).startsWith("v1:").doesNotContain(secret).isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo(secret);
        String tampered = a.substring(0, a.length() - 3) + (a.endsWith("A") ? "B" : "A") + a.substring(a.length() - 2);
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TotpSecretCipher("otra-llave", JWT_SECRET).decrypt(a)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void thePendingTokenIsNeverAnAccessTokenAndViceVersa() {
        JwtProvider jwt = new JwtProvider(JWT_SECRET, 900_000, 604_800_000);
        UUID user = UUID.randomUUID();
        String pending = jwt.generateTwoFactorPendingToken(user);
        assertThat(jwt.validateToken(pending)).as("no autentica en ningún endpoint").isFalse();
        assertThat(jwt.parseTwoFactorPendingToken(pending)).contains(user);

        String access = jwt.generateAccessToken(user, "a@b.co", "CONDUCTOR");
        assertThat(jwt.validateToken(access)).isTrue();
        assertThat(jwt.parseTwoFactorPendingToken(access)).as("un access token no sirve como token temporal").isEmpty();
        assertThat(jwt.parseTwoFactorPendingToken("basura")).isEmpty();

        JwtProvider expired = new JwtProvider(JWT_SECRET, 900_000, 604_800_000, -1_000);
        assertThat(jwt.parseTwoFactorPendingToken(expired.generateTwoFactorPendingToken(user))).isEmpty();
    }
}
