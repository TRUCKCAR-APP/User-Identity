package escuelaing.edu.co.truckcar.User_Identity.repository;

import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserRepository — integration tests con Testcontainers PostgreSQL")
class UserRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("truckcar_identity_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("findByEmail() — devuelve usuario existente")
    void findByEmail_found() {
        User user = User.builder()
                .email("test@truckcar.co")
                .passwordHash("$2a$10$hash")
                .firstName("Test")
                .lastName("User")
                .role(Role.CONDUCTOR)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("test@truckcar.co");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@truckcar.co");
    }

    @Test
    @DisplayName("findByEmail() — devuelve vacío para email inexistente")
    void findByEmail_notFound() {
        Optional<User> found = userRepository.findByEmail("noexiste@truckcar.co");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByEmail() — true para email duplicado")
    void existsByEmail_true() {
        User user = User.builder()
                .email("dup@truckcar.co")
                .passwordHash("$2a$10$hash")
                .firstName("Dup")
                .lastName("User")
                .role(Role.CONDUCTOR)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("dup@truckcar.co")).isTrue();
    }

    @Test
    @DisplayName("findAllWithFilters() — filtra por rol")
    void findAllWithFilters_byRole() {
        userRepository.save(User.builder()
                .email("conductor@truckcar.co")
                .passwordHash("$2a$10$hash")
                .firstName("C").lastName("D")
                .role(Role.CONDUCTOR).status(UserStatus.ACTIVE)
                .build());
        userRepository.save(User.builder()
                .email("coord@truckcar.co")
                .passwordHash("$2a$10$hash")
                .firstName("E").lastName("F")
                .role(Role.COORDINADOR).status(UserStatus.ACTIVE)
                .build());

        Page<User> result = userRepository.findAllWithFilters(
                Role.CONDUCTOR, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRole()).isEqualTo(Role.CONDUCTOR);
    }

    @Test
    @DisplayName("findAllWithFilters() — sin filtros devuelve todos")
    void findAllWithFilters_noFilter() {
        userRepository.save(User.builder()
                .email("a@truckcar.co").passwordHash("h")
                .firstName("A").lastName("B")
                .role(Role.CONDUCTOR).status(UserStatus.ACTIVE)
                .build());
        userRepository.save(User.builder()
                .email("b@truckcar.co").passwordHash("h")
                .firstName("C").lastName("D")
                .role(Role.ADMIN).status(UserStatus.SUSPENDED)
                .build());

        Page<User> result = userRepository.findAllWithFilters(
                null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
    }
}
