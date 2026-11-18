package escuelaing.edu.co.truckcar.User_Identity.repository;

import escuelaing.edu.co.truckcar.User_Identity.model.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    /** El código vigente más reciente del usuario (sin usar ni invalidar). */
    Optional<PasswordResetCode> findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<PasswordResetCode> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    Optional<PasswordResetCode> findByResetTokenHash(String resetTokenHash);

    @Modifying
    @Query("UPDATE PasswordResetCode c SET c.consumedAt = :now WHERE c.userId = :userId AND c.consumedAt IS NULL")
    int consumeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
