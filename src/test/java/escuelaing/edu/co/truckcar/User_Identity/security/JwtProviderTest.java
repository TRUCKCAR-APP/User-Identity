package escuelaing.edu.co.truckcar.User_Identity.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String VALID_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci10cnVja2Rhci11c2VyLWlkZW50aXR5LXRlc3Rpbmc=";

    @Test
    void failsFastWithAClearMessageWhenTheSecretIsMissing() {
        assertThatThrownBy(() -> new JwtProvider("", 60_000, 120_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRUCKCAR_JWT_SECRET");
    }

    @Test
    void rejectsSecretsShorterThan256Bits() {
        // "c2hvcnQtc2VjcmV0" decodifica a 12 bytes: insuficiente para HMAC-SHA256.
        assertThatThrownBy(() -> new JwtProvider("c2hvcnQtc2VjcmV0", 60_000, 120_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void acceptsAValidBase64Secret() {
        assertThat(new JwtProvider(VALID_SECRET, 60_000, 120_000)).isNotNull();
    }
}
