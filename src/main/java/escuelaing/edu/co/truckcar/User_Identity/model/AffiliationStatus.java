package escuelaing.edu.co.truckcar.User_Identity.model;

/**
 * Estado de la afiliación de un conductor de empresa a su transportadora.
 * <ul>
 *   <li>{@code PENDIENTE}: el conductor pidió unirse y la transportadora aún no responde.</li>
 *   <li>{@code ACTIVA}: la transportadora lo aceptó (o lo registró ella misma); puede recibir cargas.</li>
 *   <li>{@code RECHAZADA}: la transportadora no lo aceptó; no recibe cargas hasta pedir otra afiliación.</li>
 * </ul>
 */
public enum AffiliationStatus {
    PENDIENTE,
    ACTIVA,
    RECHAZADA
}
