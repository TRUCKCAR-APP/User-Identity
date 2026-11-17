package escuelaing.edu.co.truckcar.User_Identity.dto.request;

import escuelaing.edu.co.truckcar.User_Identity.model.AffiliationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Respuesta de la transportadora a una solicitud de afiliación: ACTIVA (aceptar) o RECHAZADA. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AffiliationDecisionRequest {

    @NotNull(message = "El estado es obligatorio (ACTIVA o RECHAZADA)")
    private AffiliationStatus status;
}
