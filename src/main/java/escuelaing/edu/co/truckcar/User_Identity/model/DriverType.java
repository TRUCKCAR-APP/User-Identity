package escuelaing.edu.co.truckcar.User_Identity.model;

/**
 * Tipo de conductor.
 * <ul>
 *   <li>{@code INDEPENDIENTE}: usa la navegación para camión, las rutas y las alertas viales; no recibe cargas.</li>
 *   <li>{@code EMPRESA}: conductor de una transportadora; recibe cargas cuando su afiliación está {@code ACTIVA}.</li>
 * </ul>
 * Un conductor sin tipo (cuentas anteriores a la afiliación) conserva el comportamiento de antes: puede recibir cargas.
 */
public enum DriverType {
    INDEPENDIENTE,
    EMPRESA
}
