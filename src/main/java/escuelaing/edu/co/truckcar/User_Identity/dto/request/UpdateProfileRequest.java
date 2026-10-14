package escuelaing.edu.co.truckcar.User_Identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload para que un usuario actualice su propio perfil.
 * Todos los campos son opcionales — solo se actualizan los que vengan con valor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(max = 100, message = "El nombre no debe exceder 100 caracteres")
    private String firstName;

    @Size(max = 100, message = "El apellido no debe exceder 100 caracteres")
    private String lastName;

    private String phoneNumber;

    private String documentType;

    private String documentNumber;
}
