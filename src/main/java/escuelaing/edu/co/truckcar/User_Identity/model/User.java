package escuelaing.edu.co.truckcar.User_Identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad de usuario — representa la identidad de un conductor, coordinador o administrador
 * dentro de la plataforma TruckCar.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "document_type")
    private String documentType;

    @Column(name = "document_number")
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Solo conductores: INDEPENDIENTE o EMPRESA. Nulo en cuentas anteriores a la afiliación. */
    @Enumerated(EnumType.STRING)
    @Column(name = "driver_type", length = 20)
    private DriverType driverType;

    /** Transportadora del conductor de empresa (companies.id). */
    @Column(name = "company_id")
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "affiliation_status", length = 20)
    private AffiliationStatus affiliationStatus;

    /**
     * Si el conductor puede recibir cargas según su afiliación (la verificación de la cuenta la exigen aparte los
     * servicios de cargas). Independiente: no. De empresa: solo con la afiliación ACTIVA. Sin tipo (cuentas
     * anteriores): sí, como antes.
     */
    public boolean affiliationAllowsLoads() {
        if (driverType == null) {
            return true;
        }
        return driverType == DriverType.EMPRESA && companyId != null && affiliationStatus == AffiliationStatus.ACTIVA;
    }

    @PrePersist
    protected void onCreate() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = UserStatus.PENDING_VERIFICATION;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
