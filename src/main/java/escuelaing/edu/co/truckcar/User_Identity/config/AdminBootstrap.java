package escuelaing.edu.co.truckcar.User_Identity.config;

import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el ADMIN inicial al arrancar si no hay ningún usuario con rol ADMIN, con las credenciales de
 * TRUCKCAR_ADMIN_EMAIL / TRUCKCAR_ADMIN_PASSWORD. Es idempotente: con un ADMIN ya creado no hace nada.
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 12;
    // BCrypt solo usa los primeros 72 bytes de la contraseña.
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        // Sin normalizar mayúsculas: el login busca el correo tal cual (findByEmail).
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("No existe ningún ADMIN y TRUCKCAR_ADMIN_EMAIL / TRUCKCAR_ADMIN_PASSWORD no están definidas: "
                    + "no se crea el administrador inicial");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            log.error("TRUCKCAR_ADMIN_PASSWORD debe tener entre {} y {} caracteres: no se crea el administrador inicial",
                    MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH);
            return;
        }
        if (userRepository.existsByEmail(email)) {
            // No se promueve una cuenta existente: alguien pudo registrarse con ese correo antes del arranque.
            log.error("Ya existe un usuario no ADMIN con el correo {}: no se crea el administrador inicial", email);
            return;
        }
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .firstName("Admin")
                .lastName("TruckCar")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        log.info("Administrador inicial creado: {}", email);
    }
}
