package escuelaing.edu.co.truckcar.User_Identity.event;

import java.util.UUID;

/**
 * Evento de dominio emitido cuando cambia el estado de una cuenta (verificación, suspensión, reactivación), y al
 * arrancar como fotografía del estado de cada conductor (previousStatus nulo) para que los consumidores se
 * reconcilien. fleets-drivers lo usa para que solo las cuentas ACTIVE (verificadas) entren al matching.
 *
 * @param userId         identificador del usuario
 * @param role           rol del usuario al momento del evento
 * @param previousStatus estado anterior; nulo en la fotografía de arranque
 * @param status         estado nuevo (ACTIVE, PENDING_VERIFICATION o SUSPENDED)
 * @param timestamp      instante del cambio (epoch millis)
 */
public record UserStatusChangedEvent(
        UUID userId,
        String role,
        String previousStatus,
        String status,
        long timestamp
) {
}
