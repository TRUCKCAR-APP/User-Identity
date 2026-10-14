package escuelaing.edu.co.truckcar.User_Identity.dto.request;

import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload para cambiar el estado de un usuario (ADMIN).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeStatusRequest {

    @NotNull(message = "El estado es obligatorio")
    private UserStatus status;
}
