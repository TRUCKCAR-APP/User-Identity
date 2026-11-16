package escuelaing.edu.co.truckcar.User_Identity.repository;

import escuelaing.edu.co.truckcar.User_Identity.model.RefreshToken;
import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.security.RefreshTokenHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La migración V3 convierte los refresh tokens que estaban en claro con sha256() de PostgreSQL: tiene que dar
 * exactamente lo mismo que RefreshTokenHasher, o todas las sesiones abiertas dejarían de valer tras el despliegue.
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Refresh tokens con SHA-256: la migración V3 y el servicio calculan el mismo hash")
class RefreshTokenHashMigrationIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository users;
    @Autowired
    private RefreshTokenRepository tokens;

    @Test
    @DisplayName("Un token en claro anterior a V3 se encuentra después por su hash; uno ya convertido no cambia")
    void aPlainTokenFromBeforeV3IsFoundByItsHashAfterTheMigration() throws Exception {
        User user = users.save(User.builder().email("v3@truckcar.co").passwordHash("$2a$10$hash").firstName("V")
                .lastName("Tres").role(Role.CONDUCTOR).status(UserStatus.ACTIVE).build());
        String plain = "token-en-claro-ñ-" + Instant.now().toEpochMilli();
        String alreadyHashed = RefreshTokenHasher.hash("otro-token");
        tokens.save(RefreshToken.builder().userId(user.getId()).token(plain).expiresAt(Instant.now().plusSeconds(3600)).build());
        tokens.save(RefreshToken.builder().userId(user.getId()).token(alreadyHashed).expiresAt(Instant.now().plusSeconds(3600)).build());
        tokens.flush();

        String migration = new String(new ClassPathResource("db/migration/V3__hash_refresh_tokens.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        jdbc.execute(migration);

        assertThat(tokens.findByTokenAndRevokedFalse(RefreshTokenHasher.hash(plain))).isPresent();
        assertThat(tokens.findByTokenAndRevokedFalse(plain)).isEmpty();
        assertThat(tokens.findByTokenAndRevokedFalse(alreadyHashed)).as("no se aplica el hash dos veces").isPresent();
    }
}
