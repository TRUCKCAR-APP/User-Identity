package escuelaing.edu.co.truckcar.User_Identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.mail.PasswordResetMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recuperación de contraseña de punta a punta (HTTP + PostgreSQL con la migración V6). El correo es un doble que
 * captura el código: así se ve exactamente qué se enviaría y a quién.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisplayName("Recuperar contraseña — integración con PostgreSQL")
class PasswordResetFlowIntegrationTest {

    private static final String PASSWORD = "Segura123!";
    private static final String NEW_PASSWORD = "Nueva-clave-2026";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("truckcar_identity_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl()
                + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=user_identity,public");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private DomainEventPublisher eventPublisher;
    @MockBean private PasswordResetMailer mailer;

    @BeforeEach
    void reset() {
        clearInvocations(mailer);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return mapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private String newAccount() throws Exception {
        String email = "Laura." + UUID.randomUUID().toString().substring(0, 8) + "@Truckcar.co";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s", "firstName": "Laura", "lastName": "Méndez", "role": "CONDUCTOR"}"""
                .formatted(email, PASSWORD))).andExpect(status().isCreated());
        return email;
    }

    private ResultActions forgot(String email) throws Exception {
        return postJson("/api/v1/auth/password/forgot", Map.of("email", email));
    }

    /** Pide un código y devuelve el que se envió por correo. */
    private String codeSentTo(String email) throws Exception {
        forgot(email).andExpect(status().isAccepted());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendCode(eq(email), eq("Laura"), code.capture(), eq(10L));
        clearInvocations(mailer);
        return code.getValue();
    }

    private ResultActions verifyCode(String email, String code) throws Exception {
        return postJson("/api/v1/auth/password/verify", Map.of("email", email, "code", code));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    private static String other(String code) {
        return "%06d".formatted((Integer.parseInt(code) + 1) % 1_000_000);
    }

    @Test
    void theWholeFlowChangesThePasswordAndClosesTheOtherSessions() throws Exception {
        String email = newAccount();
        String refreshToken = json(login(email, PASSWORD).andExpect(status().isOk())).path("refreshToken").asText();

        String code = codeSentTo(email);
        assertThat(code).matches("\\d{6}");
        String stored = jdbc.queryForObject("""
                SELECT c.code_hash FROM password_reset_codes c JOIN users u ON u.id = c.user_id WHERE u.email = ?""",
                String.class, email);
        assertThat(stored).hasSize(64).doesNotContain(code);

        String token = json(verifyCode(email, code).andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(600))).path("resetToken").asText();

        postJson("/api/v1/auth/password/reset", Map.of("resetToken", token, "newPassword", "corta"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/auth/password/reset", Map.of("resetToken", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk());

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken)).andExpect(status().isUnauthorized());
        // El token y el código son de un solo uso.
        postJson("/api/v1/auth/password/reset", Map.of("resetToken", token, "newPassword", "Otra-clave-2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("https://truckcar.co/errors/invalid-reset-token"));
        verifyCode(email, code).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndNoMail() throws Exception {
        String known = newAccount();
        JsonNode forKnown = json(forgot(known).andExpect(status().isAccepted()));
        JsonNode forUnknown = json(forgot("nadie." + UUID.randomUUID() + "@truckcar.co").andExpect(status().isAccepted()));
        assertThat(forUnknown).isEqualTo(forKnown);
        verify(mailer, times(1)).sendCode(anyString(), anyString(), anyString(), anyLong());
        verifyCode("nadie@truckcar.co", "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/invalid-reset-code"));
    }

    @Test
    void theEmailCanBeTypedWithOtherCapitalLetters() throws Exception {
        String email = newAccount();
        forgot(email.toLowerCase()).andExpect(status().isAccepted());
        verify(mailer).sendCode(eq(email), eq("Laura"), anyString(), eq(10L));
    }

    @Test
    void asecondCodeWithinAMinuteIsNotSent() throws Exception {
        String email = newAccount();
        codeSentTo(email);
        forgot(email).andExpect(status().isAccepted());
        verify(mailer, never()).sendCode(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void fiveWrongCodesInvalidateItEvenForTheRightOne() throws Exception {
        String email = newAccount();
        String code = codeSentTo(email);
        for (int i = 0; i < 4; i++) {
            verifyCode(email, other(code)).andExpect(status().isBadRequest());
        }
        verifyCode(email, other(code)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/reset-code-locked"));
        verifyCode(email, code).andExpect(status().isBadRequest());
    }

    @Test
    void anExpiredCodeIsRejected() throws Exception {
        String email = newAccount();
        String code = codeSentTo(email);
        jdbc.update("""
                UPDATE password_reset_codes SET expires_at = now() - interval '1 minute'
                WHERE user_id = (SELECT id FROM users WHERE email = ?)""", email);
        verifyCode(email, code).andExpect(status().isBadRequest());
    }

    @Test
    void aSuspendedAccountGetsNoCode() throws Exception {
        String email = newAccount();
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE email = ?", email);
        forgot(email).andExpect(status().isAccepted());
        verify(mailer, never()).sendCode(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void malformedRequestsAreBadRequests() throws Exception {
        forgot("no-es-un-correo").andExpect(status().isBadRequest());
        verifyCode("laura@truckcar.co", "12ab56").andExpect(status().isBadRequest());
    }
}
