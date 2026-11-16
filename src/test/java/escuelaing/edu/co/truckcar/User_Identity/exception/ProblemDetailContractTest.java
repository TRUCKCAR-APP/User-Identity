package escuelaing.edu.co.truckcar.User_Identity.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.User_Identity.security.JsonAccessDeniedHandler;
import escuelaing.edu.co.truckcar.User_Identity.security.JwtAuthenticationEntryPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errores RFC 7807 de User-Identity: los del controlador y los de los filtros de seguridad (401 sin JWT y 403 de las
 * reglas por URL, que antes salía sin cuerpo) con la misma forma y los campos de antes (error, message, path).
 */
class ProblemDetailContractTest {

    @RestController
    static class ProbeController {
        record Login(@NotBlank(message = "El email es obligatorio") @Email(message = "Email inválido") String email,
                     @NotBlank(message = "La contraseña es obligatoria") String password) { }

        @PostMapping("/api/v1/auth/login")
        String login(@Valid @RequestBody Login body) {
            throw new BadCredentialsException("x");
        }

        @GetMapping("/api/v1/users/suspendido")
        String suspended() {
            throw new AccountSuspendedException("La cuenta está suspendida; contacte a un administrador");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void badCredentialsAreA401WithTheStandardAndTheOldFields() throws Exception {
        mvc.perform(post("/api/v1/auth/login").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@truckcar.co\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/invalid-credentials"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.properties").doesNotExist());
    }

    @Test
    void aValidationErrorListsEachField() throws Exception {
        mvc.perform(post("/api/v1/auth/login").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").value("Email inválido"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[1].field").value("password"))
                .andExpect(jsonPath("$.message").value("Email inválido; La contraseña es obligatoria"));
    }

    @Test
    void problemJsonOnlyForWhoAsksForIt() throws Exception {
        mvc.perform(get("/api/v1/users/suspendido").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/v1/users/suspendido"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/account-suspended"));
    }

    @Test
    void theSecurityFiltersAnswerWithTheSameShape() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse unauthenticated = new MockHttpServletResponse();
        new JwtAuthenticationEntryPoint().commence(request, unauthenticated, new InsufficientAuthenticationException("x"));

        JsonNode body = mapper.readTree(unauthenticated.getContentAsString());
        assertThat(unauthenticated.getStatus()).isEqualTo(401);
        assertThat(unauthenticated.getContentType()).startsWith("application/json");
        assertThat(body.get("type").asText()).isEqualTo("https://truckcar.co/errors/unauthenticated");
        assertThat(body.get("message").asText()).startsWith("No autenticado");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/users/me");
        assertThat(body.has("properties")).isFalse();

        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        new JsonAccessDeniedHandler().handle(request, forbidden,
                new org.springframework.security.access.AccessDeniedException("x"));
        JsonNode denied = mapper.readTree(forbidden.getContentAsString());
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(denied.get("message").asText()).isEqualTo("No tiene permisos para esta operación");
        assertThat(denied.get("status").asInt()).isEqualTo(403);
    }
}
