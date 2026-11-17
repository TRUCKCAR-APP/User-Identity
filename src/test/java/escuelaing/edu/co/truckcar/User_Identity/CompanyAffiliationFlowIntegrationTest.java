package escuelaing.edu.co.truckcar.User_Identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.event.UserAffiliationChangedEvent;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transportadoras y afiliación de conductores de punta a punta (HTTP + PostgreSQL con la migración V4): registro de la
 * empresa, catálogo, conductor de empresa que pide afiliarse, la empresa acepta, registra, desvincula, el conductor
 * independiente y las reglas de acceso. Kafka es un mock: se comprueba el evento user.affiliation.changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisplayName("Empresas transportadoras y afiliación — integración con PostgreSQL")
class CompanyAffiliationFlowIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

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

    @BeforeEach
    void resetEvents() {
        clearInvocations(eventPublisher);
    }

    // ── Apoyo ──

    private static String unique(String prefix) {
        return prefix + "." + SEQ.incrementAndGet() + "." + UUID.randomUUID().toString().substring(0, 6) + "@truckcar.co";
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return mapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String token(ResultActions registration) throws Exception {
        return "Bearer " + json(registration.andExpect(status().isCreated())).path("accessToken").asText();
    }

    private String registerCompany(String name, String nit) throws Exception {
        return token(register("""
                {"email": "%s", "password": "Segura123!", "firstName": "Laura", "lastName": "Gómez",
                 "phoneNumber": "6015550000", "role": "TRANSPORTADORA", "companyName": "%s", "companyNit": "%s"}"""
                .formatted(unique("empresa"), name, nit)));
    }

    private String registerDriver(String driverType, UUID companyId) throws Exception {
        String company = companyId == null ? "" : ", \"companyId\": \"" + companyId + "\"";
        return token(register("""
                {"email": "%s", "password": "Segura123!", "firstName": "Diego", "lastName": "Montes",
                 "role": "CONDUCTOR", "driverType": "%s"%s}""".formatted(unique("conductor"), driverType, company)));
    }

    private JsonNode me(String auth) throws Exception {
        return json(mvc.perform(get("/api/v1/users/me").header("Authorization", auth)).andExpect(status().isOk()));
    }

    private UUID companyIdNamed(String name) throws Exception {
        JsonNode catalog = json(mvc.perform(get("/api/v1/auth/companies")).andExpect(status().isOk()));
        for (JsonNode item : catalog) {
            if (name.equals(item.path("name").asText())) {
                return UUID.fromString(item.path("id").asText());
            }
        }
        throw new AssertionError("La empresa no aparece en el catálogo: " + name);
    }

    private UserAffiliationChangedEvent lastAffiliationEvent() {
        ArgumentCaptor<UserAffiliationChangedEvent> events = ArgumentCaptor.forClass(UserAffiliationChangedEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(events.capture());
        List<UserAffiliationChangedEvent> all = events.getAllValues();
        return all.get(all.size() - 1);
    }

    // ── Pruebas ──

    @Test
    void aCompanyRegistersAppearsInTheCatalogAndAcceptsADriverWhoAskedToJoin() throws Exception {
        String company = registerCompany("Transportes Andinos", "900.123.456-7");
        UUID companyId = companyIdNamed("Transportes Andinos");

        mvc.perform(get("/api/v1/companies/me").header("Authorization", company))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value("900123456"))
                .andExpect(jsonPath("$.pendingRequests").value(0));

        String driver = registerDriver("EMPRESA", companyId);
        JsonNode pending = me(driver);
        assertThat(pending.path("driverType").asText()).isEqualTo("EMPRESA");
        assertThat(pending.path("affiliationStatus").asText()).isEqualTo("PENDIENTE");
        assertThat(pending.path("companyName").asText()).isEqualTo("Transportes Andinos");
        assertThat(pending.path("loadEligible").asBoolean()).as("pendiente: sin cargas").isFalse();
        UserAffiliationChangedEvent onRegister = lastAffiliationEvent();
        assertThat(onRegister.loadEligible()).isFalse();
        assertThat(onRegister.companyName()).isEqualTo("Transportes Andinos");

        JsonNode drivers = json(mvc.perform(get("/api/v1/companies/me/drivers").header("Authorization", company))
                .andExpect(status().isOk()));
        assertThat(drivers).hasSize(1);
        String driverId = drivers.get(0).path("id").asText();

        clearInvocations(eventPublisher);
        mvc.perform(patch("/api/v1/companies/me/drivers/{id}/affiliation", driverId).header("Authorization", company)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliationStatus").value("ACTIVA"))
                .andExpect(jsonPath("$.loadEligible").value(true));
        UserAffiliationChangedEvent accepted = lastAffiliationEvent();
        assertThat(accepted.userId()).isEqualTo(UUID.fromString(driverId));
        assertThat(accepted.loadEligible()).isTrue();
        assertThat(accepted.companyId()).isEqualTo(companyId);

        mvc.perform(get("/api/v1/companies/me").header("Authorization", company))
                .andExpect(jsonPath("$.activeDrivers").value(1));
    }

    @Test
    void theCompanyRegistersItsOwnDriverWhoStartsAffiliatedAndCanUnlinkHim() throws Exception {
        String company = registerCompany("Carga Segura", "800765432");
        String email = unique("propio");

        JsonNode created = json(mvc.perform(post("/api/v1/companies/me/drivers").header("Authorization", company)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "%s", "password": "Inicial123!", "firstName": "Jorge", "lastName": "Pardo"}"""
                                .formatted(email)))
                .andExpect(status().isCreated()));
        assertThat(created.path("role").asText()).isEqualTo("CONDUCTOR");
        assertThat(created.path("status").asText()).as("la plataforma lo verifica aparte").isEqualTo("PENDING_VERIFICATION");
        assertThat(created.path("affiliationStatus").asText()).isEqualTo("ACTIVA");
        assertThat(created.path("loadEligible").asBoolean()).isTrue();

        // El conductor entra con la contraseña inicial.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"Inicial123!\"}".formatted(email)))
                .andExpect(status().isOk());

        clearInvocations(eventPublisher);
        mvc.perform(delete("/api/v1/companies/me/drivers/{id}", created.path("id").asText()).header("Authorization", company))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverType").value("INDEPENDIENTE"))
                .andExpect(jsonPath("$.companyId").doesNotExist())
                .andExpect(jsonPath("$.loadEligible").value(false));
        assertThat(lastAffiliationEvent().driverType()).isEqualTo("INDEPENDIENTE");
    }

    @Test
    void anIndependentDriverUsesOnlyNavigationAndCanLaterAskToJoinACompany() throws Exception {
        registerCompany("Rutas del Norte", "901555444");
        UUID companyId = companyIdNamed("Rutas del Norte");

        String driver = registerDriver("INDEPENDIENTE", null);
        JsonNode independent = me(driver);
        assertThat(independent.path("driverType").asText()).isEqualTo("INDEPENDIENTE");
        assertThat(independent.path("loadEligible").asBoolean()).isFalse();
        assertThat(lastAffiliationEvent().loadEligible()).isFalse();

        mvc.perform(put("/api/v1/users/me/affiliation").header("Authorization", driver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverType\": \"EMPRESA\", \"companyId\": \"%s\"}".formatted(companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliationStatus").value("PENDIENTE"))
                .andExpect(jsonPath("$.companyName").value("Rutas del Norte"));
    }

    @Test
    void registrationRulesAndAccessAreEnforced() throws Exception {
        String company = registerCompany("Logística Sur", "830111222");

        // Mismo NIT (con otro formato): 409.
        register("""
                {"email": "%s", "password": "Segura123!", "firstName": "Otra", "lastName": "Empresa",
                 "role": "TRANSPORTADORA", "companyName": "Copia", "companyNit": "830.111.222-5"}"""
                .formatted(unique("copia"))).andExpect(status().isConflict());
        // Transportadora sin NIT: 400.
        register("""
                {"email": "%s", "password": "Segura123!", "firstName": "Sin", "lastName": "Nit",
                 "role": "TRANSPORTADORA", "companyName": "Sin NIT"}""".formatted(unique("sinnit")))
                .andExpect(status().isBadRequest());
        // Conductor de empresa sin empresa, o con una que no existe: 400.
        register("""
                {"email": "%s", "password": "Segura123!", "firstName": "A", "lastName": "B",
                 "role": "CONDUCTOR", "driverType": "EMPRESA"}""".formatted(unique("sinempresa")))
                .andExpect(status().isBadRequest());
        register("""
                {"email": "%s", "password": "Segura123!", "firstName": "A", "lastName": "B",
                 "role": "CONDUCTOR", "driverType": "EMPRESA", "companyId": "%s"}"""
                .formatted(unique("inexistente"), UUID.randomUUID()))
                .andExpect(status().isBadRequest());

        // Un conductor no gestiona empresas; una empresa no ve conductores ajenos.
        String driver = registerDriver("INDEPENDIENTE", null);
        mvc.perform(get("/api/v1/companies/me/drivers").header("Authorization", driver)).andExpect(status().isForbidden());
        String driverId = me(driver).path("id").asText();
        mvc.perform(patch("/api/v1/companies/me/drivers/{id}/affiliation", driverId).header("Authorization", company)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVA\"}"))
                .andExpect(status().isNotFound());
        // Sin sesión no se gestiona nada, pero el catálogo del registro es público.
        mvc.perform(get("/api/v1/companies/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/companies")).andExpect(status().isOk());
    }

    @Test
    void legacyDriversWithoutTypeKeepReceivingLoadsAndTheSchemaRejectsInconsistentRows() throws Exception {
        String legacy = token(register("""
                {"email": "%s", "password": "Segura123!", "firstName": "Ana", "lastName": "Ruiz", "role": "CONDUCTOR"}"""
                .formatted(unique("legacy"))));
        JsonNode profile = me(legacy);
        assertThat(profile.path("driverType").isNull() || profile.path("driverType").isMissingNode()).isTrue();
        assertThat(profile.path("loadEligible").asBoolean()).as("cuentas anteriores: como antes").isTrue();
        verify(eventPublisher, org.mockito.Mockito.never()).publish(any(UserAffiliationChangedEvent.class));

        // Un conductor de empresa sin empresa no puede existir en la base (users_affiliation_consistency_check).
        UUID id = UUID.fromString(profile.path("id").asText());
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET driver_type = 'EMPRESA' WHERE id = ?", id))
                .hasMessageContaining("users_affiliation_consistency_check");
    }
}
