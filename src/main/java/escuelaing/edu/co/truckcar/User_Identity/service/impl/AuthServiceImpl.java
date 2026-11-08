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
            // Formato exacto exigido por el test register_duplicateEmail
            throw new DuplicateResourceException("El correo ya se encuentra registrado: " + request.getEmail());
        }

        Role assignedRole = request.getRole() != null ? request.getRole() : Role.CONDUCTOR;
        if (!SELF_REGISTRABLE_ROLES.contains(assignedRole)) {
            // Excepción exacta exigida por el test register_privilegedRolesAreRejected
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

        // Regla de negocio: NO se devuelve token hasta ser aprobado
        return AuthResponse.builder().build();
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Excepción exacta exigida por el test login_wrongPassword
            throw new BadCredentialsException("Credenciales inválidas");
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new AccountPendingVerificationException("Su cuenta está en revisión. No puede iniciar sesión hasta ser aprobada por un administrador.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AccountSuspendedException("La cuenta se encuentra suspendida.");
        }

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