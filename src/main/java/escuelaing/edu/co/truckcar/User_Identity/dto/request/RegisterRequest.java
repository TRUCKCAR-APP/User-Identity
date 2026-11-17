package escuelaing.edu.co.truckcar.User_Identity.dto.request;

import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de registro de un nuevo usuario.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String password;

    @NotBlank(message = "El nombre es obligatorio")
    private String firstName;

    @NotBlank(message = "El apellido es obligatorio")
    private String lastName;

    private String phoneNumber;

    private String documentType;

    private String documentNumber;

    @NotNull(message = "El rol es obligatorio")
    private Role role;

    /** Solo CONDUCTOR: INDEPENDIENTE o EMPRESA. Si se omite, la cuenta conserva el comportamiento anterior. */
    private escuelaing.edu.co.truckcar.User_Identity.model.DriverType driverType;

    /** Solo CONDUCTOR de EMPRESA: transportadora a la que pide afiliarse (GET /api/v1/auth/companies). */
    private java.util.UUID companyId;

    /** Solo TRANSPORTADORA: razón social de la empresa. */
    @Size(max = 150, message = "El nombre de la empresa admite hasta 150 caracteres")
    private String companyName;

    /** Solo TRANSPORTADORA: NIT de la empresa (se guardan solo los dígitos, sin el de verificación si va con guion). */
    @Size(max = 20, message = "El NIT admite hasta 20 caracteres")
    private String companyNit;
}
