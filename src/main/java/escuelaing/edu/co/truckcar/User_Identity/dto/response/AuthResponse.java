package escuelaing.edu.co.truckcar.User_Identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de autenticación exitosa — contiene los tokens JWT.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    /** true si la cuenta tiene 2FA: no hay sesión todavía, falta POST /api/v1/auth/2fa/verify-login con tempToken. */
    private Boolean requiresTwoFactor;
    /** Token temporal (5 min, scope 2FA_PENDING) que solo sirve para verify-login. */
    private String tempToken;
}
