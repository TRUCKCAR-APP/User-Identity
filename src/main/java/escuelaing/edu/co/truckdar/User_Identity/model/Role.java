package escuelaing.edu.co.truckdar.User_Identity.model;

/**
 * Roles de usuario en la plataforma TruckDar.
 * <ul>
 *   <li>{@code CONDUCTOR} – conductor de carga pesada (app móvil)</li>
 *   <li>{@code COORDINADOR} – coordinador de tráfico / despachador (Torre de Control)</li>
 *   <li>{@code GENERADOR} – empresa generadora de carga: publica cargas y sigue sus envíos (Mis envíos)</li>
 *   <li>{@code ADMIN} – administrador de la plataforma</li>
 * </ul>
 */
public enum Role {
    CONDUCTOR,
    COORDINADOR,
    GENERADOR,
    ADMIN
}
