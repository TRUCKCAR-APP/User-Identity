package escuelaing.edu.co.truckcar.User_Identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResetCodeRequest {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Size(max = 255)
    private String email;

    /** 6 dígitos; se aceptan espacios ("482 913"). */
    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\s*\\d{3}\\s?\\d{3}\\s*", message = "El código tiene 6 dígitos")
    private String code;
}
