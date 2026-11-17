package escuelaing.edu.co.truckcar.User_Identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Código de 6 dígitos de la app autenticadora (o uno de respaldo, donde se acepta). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorCodeRequest {

    @NotBlank(message = "El código es obligatorio")
    private String code;
}
