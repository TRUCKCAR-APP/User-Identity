package escuelaing.edu.co.truckcar.User_Identity.dto.response;

/**
 * Secreto TOTP para registrar en la app autenticadora: la clave Base32 (para escribirla a mano), la URI otpauth:// y
 * su QR como imagen SVG en data URI. Aún no activa el 2FA.
 */
public record TwoFactorSetupResponse(String secret, String otpauthUri, String qrImage) {
}
