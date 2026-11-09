package escuelaing.edu.co.truckcar.User_Identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de alta tal como lo usa la PWA: registrarse devuelve sesión, la cuenta queda PENDING_VERIFICATION, con ese
 * token se consulta el perfil (la app muestra "Cuenta pendiente de verificación") y la cuenta pendiente puede volver
 * a iniciar sesión. Una cuenta suspendida no. Kafka se reemplaza por un mock: aquí se prueba el contrato HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisplayName("Registro y login de cuentas pendientes — integración con PostgreSQL")
class RegistrationFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("truckcar_identity_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Mismo esquema que en producción: Flyway crea las tablas en user_identity (application.yml).
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl()
                + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=user_identity,public");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @MockBean private DomainEventPublisher eventPublisher;

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }

    private String register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "Segura123!", "firstName": "Ana", "lastName": "Rodríguez",
                         "phoneNumber": "3001234567", "role": "CONDUCTOR"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions login(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"Segura123!\"}".formatted(email)));
    }

    @Test
    void registeringGivesASessionAndTheProfileShowsThePendingAccount() throws Exception {
        JsonNode session = json(register("ana.pendiente@truckcar.co"));

        String access = session.path("accessToken").asText();
        assertThat(access).as("el registro devuelve sesión, no un objeto vacío").isNotBlank();
        assertThat(session.path("refreshToken").asText()).isNotBlank();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana.pendiente@truckcar.co"))
                .andExpect(jsonPath("$.role").value("CONDUCTOR"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    @Test
    void aPendingAccountCanLogInAgainAndItsLastLoginIsRecorded() throws Exception {
        register("ana.vuelve@truckcar.co");

        login("ana.vuelve@truckcar.co")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        var user = users.findByEmail("ana.vuelve@truckcar.co").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void aSuspendedAccountCannotLogIn() throws Exception {
        register("ana.suspendida@truckcar.co");
        var user = users.findByEmail("ana.suspendida@truckcar.co").orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        users.save(user);

        login("ana.suspendida@truckcar.co")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }
}
