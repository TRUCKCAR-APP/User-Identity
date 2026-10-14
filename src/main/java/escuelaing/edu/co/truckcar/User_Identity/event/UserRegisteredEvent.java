package escuelaing.edu.co.truckcar.User_Identity.event;

import java.util.UUID;

/**
 * Evento de dominio emitido cuando un nuevo usuario se registra exitosamente.
 *
 * @param userId    identificador del nuevo usuario
 * @param email     email del usuario
 * @param role      rol asignado
 * @param timestamp instante de creación (epoch millis)
 */
public record UserRegisteredEvent(
        UUID userId,
        String email,
        String role,
        long timestamp
) {
}
