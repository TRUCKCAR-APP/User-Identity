package escuelaing.edu.co.truckcar.User_Identity.event;

import java.util.UUID;

/**
 * Evento de dominio {@code user.affiliation.changed}: tipo de conductor y afiliación a una transportadora. Se emite al
 * registrarse un conductor con tipo, cuando la transportadora acepta, rechaza o desvincula, cuando el conductor
 * cambia de tipo, y al arrancar como fotografía (igual que user.status.changed). fleets-drivers y load-allocation lo
 * usan para que un independiente o un conductor sin afiliación activa no reciba cargas.
 *
 * @param userId            conductor
 * @param driverType        INDEPENDIENTE o EMPRESA
 * @param companyId         transportadora; nulo para un independiente
 * @param companyName       nombre de la transportadora (para mostrarlo sin consultar user-identity)
 * @param affiliationStatus PENDIENTE, ACTIVA o RECHAZADA; nulo para un independiente
 * @param loadEligible      si la afiliación le permite recibir cargas (además debe tener la cuenta ACTIVE)
 * @param timestamp         instante del cambio (epoch millis)
 */
public record UserAffiliationChangedEvent(
        UUID userId,
        String driverType,
        UUID companyId,
        String companyName,
        String affiliationStatus,
        boolean loadEligible,
        long timestamp
) {
}
