package escuelaing.edu.co.truckcar.User_Identity.dto.request;

import escuelaing.edu.co.truckcar.User_Identity.model.DriverType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * El conductor elige su tipo: INDEPENDIENTE (sin empresa) o EMPRESA con la transportadora a la que pide afiliarse
 * (queda PENDIENTE hasta que la empresa responda).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AffiliationChangeRequest {

    @NotNull(message = "El tipo de conductor es obligatorio (INDEPENDIENTE o EMPRESA)")
    private DriverType driverType;

    private UUID companyId;
}
