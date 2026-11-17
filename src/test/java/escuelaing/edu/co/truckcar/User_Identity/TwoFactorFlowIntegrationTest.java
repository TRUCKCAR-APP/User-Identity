package escuelaing.edu.co.truckcar.User_Identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.security.Totp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Doble autenticación de punta a punta (HTTP + PostgreSQL con la migración V5): activación con QR y primer código,
 * login en dos pasos, token temporal que no sirve en otros endpoints, códigos de respaldo de un solo uso, código TOTP
 * no repetible, bloqueo por intentos, desactivación y cuentas sin 2FA que inician sesión igual que antes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisplayName("Doble autenticación TOTP — integración con PostgreSQL")
class TwoFactorFlowIntegrationTest {

    private static final String PASSWORD = "Segura123!";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("truckcar_identity_test")
            .withUsername("test")
            .withPassword("test");

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

    // ── Apoyo ──

    private JsonNode json(ResultActions actions) throws Exception {
        return mapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private String email(String who) {
        return who + "." + UUID.randomUUID().toString().substring(0, 8) + "@truckcar.co";
    }

    private String register(String email) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s", "firstName": "Laura", "lastName": "Méndez", "role": "CONDUCTOR"}"""
                .formatted(email, PASSWORD))).andExpect(status().isCreated())).path("accessToken").asText();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private ResultActions verifyLogin(String tempToken, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/2fa/verify-login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("tempToken", tempToken, "code", code))));
    }

    private ResultActions authPost(String path, String token, Object body) throws Exception {
        var request = post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        return mvc.perform(body == null ? request : request.content(mapper.writeValueAsString(body)));
    }

    private static String codeAt(String secret, long offsetSteps) {
        return Totp.code(secret, Totp.stepAt(System.currentTimeMillis()) + offsetSteps);
    }

    /** Un código de 6 dígitos que no es válido en la ventana actual. */
    private static String wrongCode(String secret) {
        Set<String> valid = new HashSet<>();
        for (int i = -2; i <= 2; i++) {
            valid.add(codeAt(secret, i));
        }
        for (int n = 123456; ; n++) {
            if (!valid.contains(String.valueOf(n))) {
                return String.valueOf(n);
            }
        }
    }

    /** Activa el 2FA y devuelve [secreto, códigos de respaldo...]. El paso actual queda usado. */
    private Enabled enable(String token) throws Exception {
        JsonNode setup = json(authPost("/api/v1/auth/2fa/setup", token, null).andExpect(status().isOk()));
        String secret = setup.path("secret").asText();
        JsonNode codes = json(authPost("/api/v1/auth/2fa/verify-setup", token, Map.of("code", codeAt(secret, 0)))
                .andExpect(status().isOk()));
        List<String> backup = mapper.convertValue(codes.path("backupCodes"), mapper.getTypeFactory()
                .constructCollectionType(List.class, String.class));
        return new Enabled(secret, backup);
    }

    private record Enabled(String secret, List<String> backupCodes) {
    }

    private String tempToken(String email) throws Exception {
        JsonNode first = json(login(email, PASSWORD).andExpect(status().isOk()));
        assertThat(first.path("requiresTwoFactor").asBoolean()).isTrue();
        assertThat(first.has("accessToken")).as("sin sesión antes del segundo paso").isFalse();
        assertThat(first.has("refreshToken")).isFalse();
        return first.path("tempToken").asText();
    }

    // ── Pruebas ──

    @Test
    void migrationV5AddsTheColumnsDisabledByDefaultAndEnforcesASecretWhenEnabled() {
        List<String> columns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'user_identity' AND table_name = 'users' AND column_name LIKE 'two_factor_%'""", String.class);
        assertThat(columns).containsExactlyInAnyOrder("two_factor_enabled", "two_factor_secret", "two_factor_pending_secret",
                "two_factor_backup_codes", "two_factor_last_step", "two_factor_failed_attempts", "two_factor_locked_until");
        assertThat(jdbc.queryForObject("SELECT max(version::int) FROM user_identity.flyway_schema_history WHERE success",
                Integer.class)).isGreaterThanOrEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'user_identity' AND table_name = 'users' AND column_name = 'two_factor_enabled'""",
                String.class)).isEqualTo("false");
    }

    @Test
    void anAccountWithoutTwoFactorLogsInExactlyAsBefore() throws Exception {
        String email = email("sin2fa");
        String token = register(email);
        login(email, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.requiresTwoFactor").doesNotExist())
                .andExpect(jsonPath("$.tempToken").doesNotExist());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.twoFactorEnabled").value(false));
    }

    @Test
    void enablingTwoFactorThenLoggingInTakesTwoStepsAndTheTempTokenOpensNothingElse() throws Exception {
        String email = email("con2fa");
        String token = register(email);

        // Gestionar el 2FA exige sesión.
        mvc.perform(post("/api/v1/auth/2fa/setup")).andExpect(status().isUnauthorized());

        JsonNode setup = json(authPost("/api/v1/auth/2fa/setup", token, null).andExpect(status().isOk()));
        String secret = setup.path("secret").asText();
        assertThat(secret).matches("[A-Z2-7]{32}");
        assertThat(setup.path("otpauthUri").asText())
                .startsWith("otpauth://totp/TruckCar:" + email.replace("@", "%40"))
                .contains("secret=" + secret).contains("issuer=TruckCar");
        assertThat(setup.path("qrImage").asText()).startsWith("data:image/svg+xml;base64,");
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.twoFactorEnabled").value(false)); // setup no activa nada

        authPost("/api/v1/auth/2fa/verify-setup", token, Map.of("code", wrongCode(secret)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/invalid-2fa-code"));
        JsonNode codes = json(authPost("/api/v1/auth/2fa/verify-setup", token, Map.of("code", codeAt(secret, 0)))
                .andExpect(status().isOk()));
        assertThat(codes.path("backupCodes")).hasSize(8);
        assertThat(codes.path("backupCodes").get(0).asText()).matches("[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}");

        // En la base: secreto cifrado y solo los SHA-256 de los códigos de respaldo.
        Map<String, Object> row = jdbc.queryForMap("SELECT two_factor_enabled, two_factor_secret, two_factor_backup_codes "
                + "FROM user_identity.users WHERE email = ?", email);
        assertThat(row.get("two_factor_enabled")).isEqualTo(true);
        assertThat((String) row.get("two_factor_secret")).startsWith("v1:").doesNotContain(secret);
        assertThat((String) row.get("two_factor_backup_codes")).doesNotContain(codes.path("backupCodes").get(0).asText())
                .contains(escuelaing.edu.co.truckcar.User_Identity.security.RefreshTokenHasher.hash(
                        codes.path("backupCodes").get(0).asText().replace("-", "")));

        // Paso 1: contraseña → token temporal, sin sesión.
        String temp = tempToken(email);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + temp)).andExpect(status().isUnauthorized());
        authPost("/api/v1/auth/2fa/setup", temp, null).andExpect(status().isUnauthorized());
        authPost("/api/v1/auth/2fa/disable", temp, Map.of("password", PASSWORD)).andExpect(status().isUnauthorized());

        // Paso 2: código inválido → 401 RFC 7807 en application/json; válido → sesión completa.
        verifyLogin(temp, wrongCode(secret)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Código de verificación inválido"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
        String nextStep = codeAt(secret, 1); // el paso actual ya se usó al activar
        JsonNode session = json(verifyLogin(temp, nextStep).andExpect(status().isOk()));
        String access = session.path("accessToken").asText();
        assertThat(session.path("refreshToken").asText()).isNotBlank();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorEnabled").value(true));

        // El mismo código no sirve dos veces, ni con otro token temporal.
        verifyLogin(tempToken(email), nextStep).andExpect(status().isUnauthorized());
        // Un token temporal falso o de otro tipo no sirve.
        verifyLogin(access, codeAt(secret, 0)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/invalid-2fa-token"));

        // Cada código de respaldo sirve una sola vez.
        String backup = codes.path("backupCodes").get(3).asText();
        verifyLogin(tempToken(email), backup.toLowerCase()).andExpect(status().isOk());
        verifyLogin(tempToken(email), backup).andExpect(status().isUnauthorized());

        // Activar otra vez sin desactivar: 409.
        authPost("/api/v1/auth/2fa/setup", access, null).andExpect(status().isConflict());
    }

    @Test
    void repeatedWrongCodesLockTheSecondStepForAWhile() throws Exception {
        String email = email("bloqueo");
        Enabled enabled = enable(register(email));
        String temp = tempToken(email);
        for (int i = 0; i < 5; i++) {
            verifyLogin(temp, wrongCode(enabled.secret())).andExpect(status().isUnauthorized());
        }
        verifyLogin(temp, codeAt(enabled.secret(), 1)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/2fa-locked"));
        verifyLogin(temp, enabled.backupCodes().get(0)).andExpect(status().isTooManyRequests());
    }

    @Test
    void disablingRequiresThePasswordOrACodeAndLoginGoesBackToOneStep() throws Exception {
        String email = email("desactiva");
        String token = register(email);
        Enabled enabled = enable(token);

        authPost("/api/v1/auth/2fa/disable", token, Map.of()).andExpect(status().isBadRequest());
        authPost("/api/v1/auth/2fa/disable", token, Map.of("password", "otra-clave")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/2fa-disable-denied"));
        authPost("/api/v1/auth/2fa/disable", token, Map.of("code", enabled.backupCodes().get(1))).andExpect(status().isOk());

        login(email, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.requiresTwoFactor").doesNotExist());
        Map<String, Object> row = jdbc.queryForMap("SELECT two_factor_enabled, two_factor_secret, two_factor_backup_codes "
                + "FROM user_identity.users WHERE email = ?", email);
        assertThat(row.get("two_factor_enabled")).isEqualTo(false);
        assertThat(row.get("two_factor_secret")).isNull();
        assertThat(row.get("two_factor_backup_codes")).isNull();
    }
}
