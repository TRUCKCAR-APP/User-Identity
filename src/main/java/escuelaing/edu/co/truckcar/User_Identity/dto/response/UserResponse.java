package escuelaing.edu.co.truckcar.User_Identity.dto.response;

import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Representación pública de un usuario — nunca expone el passwordHash.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String documentType;
    private String documentNumber;
    private Role role;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    /** Solo conductores: INDEPENDIENTE o EMPRESA (nulo en cuentas anteriores a la afiliación). */
    private escuelaing.edu.co.truckcar.User_Identity.model.DriverType driverType;
    private UUID companyId;
    private String companyName;
    private escuelaing.edu.co.truckcar.User_Identity.model.AffiliationStatus affiliationStatus;
    /** Si la afiliación le permite recibir cargas (la cuenta además debe estar ACTIVE). */
    private Boolean loadEligible;
    /** Doble autenticación activa. */
    private boolean twoFactorEnabled;
}
