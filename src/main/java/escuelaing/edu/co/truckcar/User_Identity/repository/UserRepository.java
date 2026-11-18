package escuelaing.edu.co.truckcar.User_Identity.repository;

import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de usuarios — Spring Data JPA genera la implementación.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    /** Recuperación de contraseña: el conductor puede escribir el correo con otras mayúsculas que al registrarse. */
    Optional<User> findFirstByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    long countByRoleAndStatus(Role role, UserStatus status);

    /** Conductores afiliados o con solicitud a una transportadora, del más reciente al más antiguo. */
    java.util.List<User> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    long countByCompanyIdAndAffiliationStatus(UUID companyId,
                                              escuelaing.edu.co.truckcar.User_Identity.model.AffiliationStatus status);

    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<User> findAllWithFilters(
            @Param("role") Role role,
            @Param("status") UserStatus status,
            Pageable pageable
    );
}
