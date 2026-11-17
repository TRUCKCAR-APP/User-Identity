package escuelaing.edu.co.truckcar.User_Identity.service.impl;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.LoginRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.RefreshTokenRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.RegisterRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.TwoFactorLoginRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.AuthResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.MessageResponse;
import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.event.UserRegisteredEvent;
import escuelaing.edu.co.truckcar.User_Identity.exception.*;
import escuelaing.edu.co.truckcar.User_Identity.model.RefreshToken;
import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.repository.RefreshTokenRepository;
import escuelaing.edu.co.truckcar.User_Identity.security.RefreshTokenHasher;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import escuelaing.edu.co.truckcar.User_Identity.security.JwtProvider;
import escuelaing.edu.co.truckcar.User_Identity.service.AuthService;
import escuelaing.edu.co.truckcar.User_Identity.service.CompanyService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    // Se restaura la validación de roles exigida por el test
    private static final Set<Role> SELF_REGISTRABLE_ROLES = EnumSet.of(Role.CONDUCTOR, Role.GENERADOR, Role.TRANSPORTADORA);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final DomainEventPublisher eventPublisher;
    private final CompanyService companyService;
    private final escuelaing.edu.co.truckcar.User_Identity.service.TwoFactorService twoFactorService;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtProvider jwtProvider,
                           DomainEventPublisher eventPublisher,
                           CompanyService companyService,
                           escuelaing.edu.co.truckcar.User_Identity.service.TwoFactorService twoFactorService) {
        this.twoFactorService = twoFactorService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.eventPublisher = eventPublisher;
        this.companyService = companyService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("El correo ya se encuentra registrado: " + request.getEmail());
        }

        Role assignedRole = request.getRole() != null ? request.getRole() : Role.CONDUCTOR;
        if (!SELF_REGISTRABLE_ROLES.contains(assignedRole)) {
            // COORDINADOR y ADMIN solo los asigna un ADMIN con PATCH /api/v1/users/{id}/role.
            throw new IllegalArgumentException("Rol no permitido");
        }
        companyService.validateRegistration(assignedRole, request);

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .role(assignedRole)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();
        if (assignedRole == Role.CONDUCTOR) {
            companyService.applyDriverRegistration(user, request);
        }

        User savedUser = userRepository.save(user);
        if (assignedRole == Role.TRANSPORTADORA) {
            companyService.createCompanyFor(savedUser, request);
        }

        eventPublisher.publish(new UserRegisteredEvent(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().name(),
                Instant.now().toEpochMilli()
        ));
        companyService.publishAffiliation(savedUser);

        // La cuenta nace PENDING_VERIFICATION y recibe sesión: el conductor entra, ve que su cuenta está pendiente y
        // registra su vehículo mientras un administrador la verifica. Lo que una cuenta pendiente no puede hacer lo
        // limitan los servicios: ai-fleet-allocation no le asigna cargas hasta que user.status.changed la marca ACTIVE.
        return generateAuthResponse(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        // La contraseña se comprueba antes que el estado: solo quien la conoce sabe si la cuenta está suspendida.
        // Una cuenta PENDING_VERIFICATION sí inicia sesión (ver register).
        requireNotSuspended(user);
        if (user.isTwoFactorEnabled()) {
            // Con 2FA no hay sesión todavía: solo un token temporal para POST /api/v1/auth/2fa/verify-login.
            return AuthResponse.builder()
                    .requiresTwoFactor(true)
                    .tempToken(jwtProvider.generateTwoFactorPendingToken(user.getId()))
                    .expiresIn(jwtProvider.getPendingTokenTtlMs() / 1000)
                    .build();
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return generateAuthResponse(user);
    }

    /** Segundo paso: token temporal válido y código TOTP o de respaldo → sesión completa. */
    @Override
    @Transactional(noRollbackFor = TwoFactorException.class)
    public AuthResponse verifyTwoFactorLogin(TwoFactorLoginRequest request) {
        UUID userId = jwtProvider.parseTwoFactorPendingToken(request.getTempToken())
                .orElseThrow(TwoFactorException::invalidPendingToken);
        User user = userRepository.findById(userId).orElseThrow(TwoFactorException::invalidPendingToken);
        requireNotSuspended(user);
        if (!user.isTwoFactorEnabled()) {
            throw TwoFactorException.invalidPendingToken();
        }
        twoFactorService.verifyLoginCode(user, request.getCode());
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return generateAuthResponse(user);
    }

    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository
                .findByTokenAndRevokedFalse(RefreshTokenHasher.hash(request.getRefreshToken()))
                .orElseThrow(() -> new InvalidTokenException("Refresh token inválido o revocado"));

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new InvalidTokenException("Refresh token expirado");
        }

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        requireNotSuspended(user);

        if (refreshTokenRepository.revokeIfActive(storedToken.getId()) == 0) {
            throw new InvalidTokenException("Refresh token inválido o revocado");
        }

        return generateAuthResponse(user);
    }

    @Override
    public MessageResponse logout(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        return MessageResponse.builder()
                .message("Sesión cerrada exitosamente")
                .build();
    }

    private static void requireNotSuspended(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AccountSuspendedException("La cuenta está suspendida; contacte a un administrador");
        }
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());

        String rawRefreshToken = jwtProvider.generateRefreshToken();

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .token(RefreshTokenHasher.hash(rawRefreshToken))
                .expiresAt(Instant.now().plusMillis(jwtProvider.getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProvider.getAccessTokenExpirationMs() / 1000)
                .build();
    }
}