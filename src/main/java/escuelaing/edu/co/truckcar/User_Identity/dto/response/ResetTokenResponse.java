package escuelaing.edu.co.truckcar.User_Identity.dto.response;

/** Token de un solo uso para fijar la contraseña nueva. */
public record ResetTokenResponse(String resetToken, long expiresInSeconds) {
}
