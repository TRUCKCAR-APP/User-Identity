package escuelaing.edu.co.truckcar.User_Identity.config;

import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AdminBootstrap — ADMIN inicial desde variables de entorno")
class AdminBootstrapTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("Segura-Admin-2026")).thenReturn("$2a$10$hash");
    }

    private void run(String email, String password) {
        new AdminBootstrap(userRepository, passwordEncoder, email, password).run(null);
    }

    @Test
    @DisplayName("crea el ADMIN con la contraseña cifrada si no existe ninguno")
    void createsAdminWhenNoneExists() {
        run("admin@truckcar.co", "Segura-Admin-2026");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@truckcar.co");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("no hace nada si ya hay un ADMIN")
    void isIdempotent() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        run("admin@truckcar.co", "Segura-Admin-2026");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("sin variables, con contraseña corta o con el correo ya registrado no crea nada")
    void skipsInvalidConfiguration() {
        run("", "");
        run("admin@truckcar.co", "corta");
        when(userRepository.existsByEmail("conductor@truckcar.co")).thenReturn(true);
        run("conductor@truckcar.co", "Segura-Admin-2026");

        verify(userRepository, never()).save(any());
    }
}
