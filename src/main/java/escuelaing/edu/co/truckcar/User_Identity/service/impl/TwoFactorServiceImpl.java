package escuelaing.edu.co.truckcar.User_Identity.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.TwoFactorDisableRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.MessageResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.TwoFactorBackupCodesResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.TwoFactorSetupResponse;
import escuelaing.edu.co.truckcar.User_Identity.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckcar.User_Identity.exception.TwoFactorException;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.repository.RefreshTokenRepository;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import escuelaing.edu.co.truckcar.User_Identity.security.RefreshTokenHasher;
import escuelaing.edu.co.truckcar.User_Identity.security.Totp;
import escuelaing.edu.co.truckcar.User_Identity.security.TotpSecretCipher;
import escuelaing.edu.co.truckcar.User_Identity.service.TwoFactorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TwoFactorServiceImpl implements TwoFactorService {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorServiceImpl.class);
    private static final int BACKUP_CODES = 8;
    private static final String BACKUP_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // sin 0/O ni 1/I/L
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TotpSecretCipher cipher;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final String issuer;
    private final int maxFailedAttempts;
    private final long lockoutMs;
    private Clock clock = Clock.systemUTC();

    public TwoFactorServiceImpl(UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                TotpSecretCipher cipher,
                                PasswordEncoder passwordEncoder,
                                ObjectMapper objectMapper,
                                @Value("${app.two-factor.issuer:TruckCar}") String issuer,
                                @Value("${app.two-factor.max-failed-attempts:5}") int maxFailedAttempts,
                                @Value("${app.two-factor.lockout-ms:900000}") long lockoutMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cipher = cipher;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.issuer = issuer;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutMs = lockoutMs;
    }

    /** Solo para pruebas: reloj fijo. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ── Configuración ──

    @Override
    @Transactional
    public TwoFactorSetupResponse setup(UUID userId) {
        User user = findUser(userId);
        if (user.isTwoFactorEnabled()) {
            throw new TwoFactorException(HttpStatus.CONFLICT, "2fa-already-enabled", "Doble autenticación ya activa",
                    "La doble autenticación ya está activa. Desactívela primero si quiere registrar otro teléfono");
        }
        String secret = Totp.newSecret();
        user.setTwoFactorPendingSecret(cipher.encrypt(secret));
        userRepository.save(user);
        String label = enc(issuer) + ":" + enc(user.getEmail());
        String uri = "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + Totp.DIGITS + "&period=" + Totp.STEP_SECONDS;
        return new TwoFactorSetupResponse(secret, uri, qrSvg(uri));
    }

    @Override
    @Transactional(noRollbackFor = TwoFactorException.class)
    public TwoFactorBackupCodesResponse verifySetup(UUID userId, String code) {
        User user = findUser(userId);
        if (user.isTwoFactorEnabled()) {
            throw new TwoFactorException(HttpStatus.CONFLICT, "2fa-already-enabled", "Doble autenticación ya activa",
                    "La doble autenticación ya está activa");
        }
        if (user.getTwoFactorPendingSecret() == null) {
            throw new TwoFactorException(HttpStatus.BAD_REQUEST, "2fa-setup-missing", "Configuración no iniciada",
                    "Primero pida el código QR con POST /api/v1/auth/2fa/setup");
        }
        requireNotLocked(user);
        String secret = cipher.decrypt(user.getTwoFactorPendingSecret());
        long step = Totp.matchStep(secret, normalizeTotp(code), clock.millis(), 1, null);
        if (step < 0) {
            registerFailure(user);
            throw TwoFactorException.invalidCode();
        }
        List<String> codes = newBackupCodes();
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret(user.getTwoFactorPendingSecret());
        user.setTwoFactorPendingSecret(null);
        user.setTwoFactorLastStep(step);
        user.setTwoFactorBackupCodes(writeHashes(codes.stream().map(TwoFactorServiceImpl::hashBackupCode).toList()));
        user.setTwoFactorFailedAttempts(0);
        user.setTwoFactorLockedUntil(null);
        userRepository.save(user);
        log.info("Doble autenticación activada para el usuario {}", user.getId());
        return new TwoFactorBackupCodesResponse(codes,
                "Guarde estos códigos en un lugar seguro: cada uno sirve una sola vez si pierde el teléfono y no se volverán a mostrar");
    }

    @Override
    @Transactional(noRollbackFor = TwoFactorException.class)
    public MessageResponse disable(UUID userId, TwoFactorDisableRequest request) {
        User user = findUser(userId);
        if (!user.isTwoFactorEnabled()) {
            throw new TwoFactorException(HttpStatus.CONFLICT, "2fa-not-enabled", "Doble autenticación inactiva",
                    "La doble autenticación no está activa en esta cuenta");
        }
        String password = request == null ? null : request.getPassword();
        String code = request == null ? null : request.getCode();
        if ((password == null || password.isBlank()) && (code == null || code.isBlank())) {
            throw new IllegalArgumentException("Envíe su contraseña o un código de verificación para desactivar la doble autenticación");
        }
        boolean authorized = password != null && !password.isBlank() && passwordEncoder.matches(password, user.getPasswordHash());
        if (!authorized && code != null && !code.isBlank()) {
            requireNotLocked(user);
            authorized = consumeCode(user, code);
        }
        if (!authorized) {
            registerFailure(user);
            throw new TwoFactorException(HttpStatus.UNAUTHORIZED, "2fa-disable-denied", "No se pudo desactivar",
                    "La contraseña o el código no son válidos");
        }
        clear(user);
        userRepository.save(user);
        // Quien tenga una sesión abierta en otro dispositivo debe volver a entrar.
        refreshTokenRepository.revokeAllByUserId(user.getId());
        log.info("Doble autenticación desactivada para el usuario {}", user.getId());
        return MessageResponse.builder().message("Doble autenticación desactivada").build();
    }

    // ── Login ──

    @Override
    @Transactional(noRollbackFor = TwoFactorException.class)
    public void verifyLoginCode(User user, String code) {
        requireNotLocked(user);
        if (!consumeCode(user, code)) {
            registerFailure(user);
            throw TwoFactorException.invalidCode();
        }
        user.setTwoFactorFailedAttempts(0);
        user.setTwoFactorLockedUntil(null);
        userRepository.save(user);
    }

    /** TOTP (no repetido) o código de respaldo (se elimina al usarlo). */
    private boolean consumeCode(User user, String rawCode) {
        String totp = normalizeTotp(rawCode);
        if (totp.matches("\\d{" + Totp.DIGITS + "}")) {
            long step = Totp.matchStep(cipher.decrypt(user.getTwoFactorSecret()), totp, clock.millis(), 1, user.getTwoFactorLastStep());
            if (step >= 0) {
                user.setTwoFactorLastStep(step);
                return true;
            }
            return false;
        }
        List<String> hashes = new ArrayList<>(readHashes(user.getTwoFactorBackupCodes()));
        String hash = hashBackupCode(rawCode);
        if (hash != null && hashes.remove(hash)) {
            user.setTwoFactorBackupCodes(writeHashes(hashes));
            log.info("Usuario {} usó un código de respaldo; le quedan {}", user.getId(), hashes.size());
            return true;
        }
        return false;
    }

    private void requireNotLocked(User user) {
        Instant until = user.getTwoFactorLockedUntil();
        if (until != null && until.isAfter(clock.instant())) {
            long minutes = Math.max(1, (until.toEpochMilli() - clock.millis() + 59_999) / 60_000);
            throw new TwoFactorException(HttpStatus.TOO_MANY_REQUESTS, "2fa-locked", "Demasiados intentos",
                    "Demasiados códigos inválidos. Intente de nuevo en " + minutes + (minutes == 1 ? " minuto" : " minutos"));
        }
    }

    private void registerFailure(User user) {
        int failures = user.getTwoFactorFailedAttempts() + 1;
        if (failures >= maxFailedAttempts) {
            user.setTwoFactorLockedUntil(clock.instant().plusMillis(lockoutMs));
            failures = 0;
            log.warn("Doble autenticación bloqueada temporalmente para el usuario {} por códigos inválidos", user.getId());
        }
        user.setTwoFactorFailedAttempts(failures);
        userRepository.save(user);
    }

    private static void clear(User user) {
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setTwoFactorPendingSecret(null);
        user.setTwoFactorBackupCodes(null);
        user.setTwoFactorLastStep(null);
        user.setTwoFactorFailedAttempts(0);
        user.setTwoFactorLockedUntil(null);
    }

    // ── Apoyo ──

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + userId));
    }

    private static String normalizeTotp(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "");
    }

    /** Códigos con el formato XXXX-XXXX, sin caracteres ambiguos. */
    static List<String> newBackupCodes() {
        List<String> codes = new ArrayList<>(BACKUP_CODES);
        for (int i = 0; i < BACKUP_CODES; i++) {
            StringBuilder code = new StringBuilder(9);
            for (int j = 0; j < 8; j++) {
                if (j == 4) {
                    code.append('-');
                }
                code.append(BACKUP_ALPHABET.charAt(RANDOM.nextInt(BACKUP_ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return codes;
    }

    /** SHA-256 del código sin guion ni espacios y en mayúsculas (como los refresh tokens), o null si está vacío. */
    static String hashBackupCode(String code) {
        if (code == null) {
            return null;
        }
        String clean = code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return clean.isEmpty() ? null : RefreshTokenHasher.hash(clean);
    }

    private List<String> readHashes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Códigos de respaldo ilegibles", e);
        }
    }

    private String writeHashes(List<String> hashes) {
        try {
            return objectMapper.writeValueAsString(hashes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** QR de la URI como SVG (sin AWT): un rectángulo por módulo negro. */
    static String qrSvg(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            int size = matrix.getWidth();
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (matrix.get(x, y)) {
                        path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                    }
                }
            }
            String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size
                    + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                    + "<path fill=\"#000\" d=\"" + path + "\"/></svg>";
            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el código QR", e);
        }
    }
}
