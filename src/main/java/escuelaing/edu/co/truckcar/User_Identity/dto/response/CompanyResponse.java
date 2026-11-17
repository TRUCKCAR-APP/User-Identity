package escuelaing.edu.co.truckcar.User_Identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Empresa transportadora con el conteo de sus conductores. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyResponse {
    private UUID id;
    private String name;
    private String nit;
    private String phoneNumber;
    private UUID ownerUserId;
    private Instant createdAt;
    /** Conductores con la afiliación ACTIVA. */
    private long activeDrivers;
    /** Solicitudes de afiliación PENDIENTE. */
    private long pendingRequests;
}
