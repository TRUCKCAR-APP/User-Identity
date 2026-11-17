package escuelaing.edu.co.truckcar.User_Identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Segundo paso del login: el token temporal de /auth/login y el código TOTP o de respaldo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorLoginRequest {

    @NotBlank(message = "El token temporal es obligatorio")
    private String tempToken;

    @NotBlank(message = "El código es obligatorio")
    private String code;
}
