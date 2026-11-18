package escuelaing.edu.co.truckcar.User_Identity.dto.response;

/**
 * Respuesta igual exista o no la cuenta: no revela qué correos están registrados.
 *
 * @param message            texto para mostrar
 * @param expiresInSeconds   vigencia del código
 * @param resendAfterSeconds espera antes de pedir otro
 */
public record ForgotPasswordResponse(String message, long expiresInSeconds, long resendAfterSeconds) {
}
