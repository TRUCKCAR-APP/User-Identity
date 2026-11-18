package escuelaing.edu.co.truckcar.User_Identity.service;

import escuelaing.edu.co.truckcar.User_Identity.exception.PasswordResetException;
import escuelaing.edu.co.truckcar.User_Identity.mail.PasswordResetMailer;
import escuelaing.edu.co.truckcar.User_Identity.model.PasswordResetCode;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.repository.PasswordResetCodeRepository;
import escuelaing.edu.co.truckcar.User_Identity.repository.RefreshTokenRepository;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import escuelaing.edu.co.truckcar.User_Identity.security.RefreshTokenHasher;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Recuperación de contraseña con un código de 6 dígitos al correo.
 * <ol>
 *   <li>{@link #requestCode}: si el correo tiene una cuenta no suspendida, genera el código (10 min) y lo envía.
 *       Responde igual exista o no la cuenta. Como máximo un código por minuto y cinco por hora por cuenta.</li>
 *   <li>{@link #verifyCode}: con el código correcto entrega un token de un solo uso (10 min); a los 5 códigos
 *       equivocados el código se invalida.</li>
 *   <li>{@link #resetPassword}: fija la contraseña nueva, invalida los demás códigos y cierra todas las sesiones.</li>
 * </ol>
 */
@Slf4j
@Service
public class PasswordResetService {

    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    public static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    public static final Duration RESEND_AFTER = Duration.ofSeconds(60);
    static final int MAX_CODES_PER_HOUR = 5;
    static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final SecretKeySpec hmacKey;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PasswordResetService(UserRepository users, PasswordResetCodeRepository codes, RefreshTokenRepository refreshTokens,
                                PasswordEncoder passwordEncoder, PasswordResetMailer mailer,
                                @Value("${app.jwt.secret}") String jwtSecret) {
        this(users, codes, refreshTokens, passwordEncoder, mailer, jwtSecret, Clock.systemUTC());
    }

    PasswordResetService(UserRepository users, PasswordResetCodeRepository codes, RefreshTokenRepository refreshTokens,
                         PasswordEncoder passwordEncoder, PasswordResetMailer mailer, String jwtSecret, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.hmacKey = new SecretKeySpec(sha256Bytes("truckcar-password-reset-v1:" + jwtSecret), "HmacSHA256");
        this.clock = clock;
    }

    @Transactional
    public void requestCode(String email) {
        Optional<User> found = users.findFirstByEmailIgnoreCase(normalize(email));
        if (found.isEmpty() || found.get().getStatus() == UserStatus.SUSPENDED) {
            Metrics.counter("truckcar.password_reset.requests", "outcome", "ignored").increment();
            return;
        }
        User user = found.get();
        Instant now = clock.instant();
        Optional<PasswordResetCode> last = codes.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (last.isPresent() && last.get().getCreatedAt().plus(RESEND_AFTER).isAfter(now)) {
            Metrics.counter("truckcar.password_reset.requests", "outcome", "too_soon").increment();
            return;
        }
        if (codes.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1))) >= MAX_CODES_PER_HOUR) {
            Metrics.counter("truckcar.password_reset.requests", "outcome", "hourly_limit").increment();
            return;
        }
        codes.consumeAllForUser(user.getId(), now);
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        codes.save(PasswordResetCode.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .codeHash(codeHash(user.getId(), code))
                .expiresAt(now.plus(CODE_TTL))
                .createdAt(now)
                .build());
        mailer.sendCode(user.getEmail(), user.getFirstName(), code, CODE_TTL.toMinutes());
        Metrics.counter("truckcar.password_reset.requests", "outcome", "sent").increment();
    }

    /** Devuelve el token de un solo uso para fijar la contraseña nueva. */
    @Transactional(noRollbackFor = PasswordResetException.class)
    public String verifyCode(String email, String code) {
        User user = users.findFirstByEmailIgnoreCase(normalize(email)).orElseThrow(PasswordResetException::invalidCode);
        Instant now = clock.instant();
        PasswordResetCode current = codes.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId())
                .filter(c -> c.getResetTokenHash() == null && c.getExpiresAt().isAfter(now))
                .orElseThrow(PasswordResetException::invalidCode);
        String given = code == null ? "" : code.replaceAll("\\s", "");
        if (!MessageDigest.isEqual(current.getCodeHash().getBytes(StandardCharsets.UTF_8),
                codeHash(user.getId(), given).getBytes(StandardCharsets.UTF_8))) {
            current.setAttempts(current.getAttempts() + 1);
            if (current.getAttempts() >= MAX_ATTEMPTS) {
                current.setConsumedAt(now);
                codes.save(current);
                throw PasswordResetException.tooManyAttempts();
            }
            codes.save(current);
            throw PasswordResetException.invalidCode();
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        current.setResetTokenHash(RefreshTokenHasher.hash(token));
        current.setTokenExpiresAt(now.plus(TOKEN_TTL));
        codes.save(current);
        return token;
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetCode current = codes.findByResetTokenHash(RefreshTokenHasher.hash(resetToken == null ? "" : resetToken))
                .filter(c -> c.getConsumedAt() == null && c.getTokenExpiresAt() != null && c.getTokenExpiresAt().isAfter(now))
                .orElseThrow(PasswordResetException::invalidToken);
        User user = users.findById(current.getUserId()).orElseThrow(PasswordResetException::invalidToken);
        if (user.getStatus() == UserStatus.SUSPENDED) throw PasswordResetException.invalidToken();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        codes.consumeAllForUser(user.getId(), now);
        refreshTokens.revokeAllByUserId(user.getId());
        Metrics.counter("truckcar.password_reset.completed").increment();
        log.info("Contraseña cambiada con código de recuperación para {}", PasswordResetMailer.mask(user.getEmail()));
    }

    String codeHash(UUID userId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal((userId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip();
    }

    private static byte[] sha256Bytes(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
