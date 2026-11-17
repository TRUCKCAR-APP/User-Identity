package escuelaing.edu.co.truckcar.User_Identity.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Desactivar el 2FA exige la contraseña o un código vigente (TOTP o de respaldo). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorDisableRequest {

    private String password;

    private String code;
}
