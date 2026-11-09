package escuelaing.edu.co.truckcar.User_Identity.service.impl;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.LoginRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.RefreshTokenRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.RegisterRequest;
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
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import escuelaing.edu.co.truckcar.User_Identity.security.JwtProvider;
import escuelaing.edu.co.truckcar.User_Identity.service.AuthService;
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
    private static final Set<Role> SELF_REGISTRABLE_ROLES = EnumSet.of(Role.CONDUCTOR, Role.GENERADOR);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final DomainEventPublisher eventPublisher;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtProvider jwtProvider,
                           DomainEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.eventPublisher = eventPublisher;
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

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .role(assignedRole)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();

        User savedUser = userRepository.save(user);

        eventPublisher.publish(new UserRegisteredEvent(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().name(),
                Instant.now().toEpochMilli()
        ));

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
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return generateAuthResponse(user);
    }

    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository
                .findByTokenAndRevokedFalse(request.getRefreshToken())
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
                .token(rawRefreshToken)
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