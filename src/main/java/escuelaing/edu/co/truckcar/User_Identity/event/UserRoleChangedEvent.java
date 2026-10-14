package escuelaing.edu.co.truckcar.User_Identity.event;

import java.util.UUID;

/**
 * Evento de dominio emitido cuando se cambia el rol de un usuario.
 *
 * @param userId   identificador del usuario
 * @param oldRole  rol anterior
 * @param newRole  nuevo rol
 * @param timestamp instante del cambio (epoch millis)
 */
public record UserRoleChangedEvent(
        UUID userId,
        String oldRole,
        String newRole,
        long timestamp
) {
}
