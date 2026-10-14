package escuelaing.edu.co.truckcar.User_Identity.dto.request;

import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload para cambiar el rol de un usuario (ADMIN).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeRoleRequest {

    @NotNull(message = "El rol es obligatorio")
    private Role role;
}
