package escuelaing.edu.co.truckdar.User_Identity.service.impl;

import escuelaing.edu.co.truckdar.User_Identity.dto.request.LoginRequest;
import escuelaing.edu.co.truckdar.User_Identity.dto.request.RefreshTokenRequest;
import escuelaing.edu.co.truckdar.User_Identity.dto.request.RegisterRequest;
import escuelaing.edu.co.truckdar.User_Identity.dto.response.AuthResponse;
import escuelaing.edu.co.truckdar.User_Identity.dto.response.MessageResponse;
import escuelaing.edu.co.truckdar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckdar.User_Identity.event.UserRegisteredEvent;
import escuelaing.edu.co.truckdar.User_Identity.exception.DuplicateResourceException;
import escuelaing.edu.co.truckdar.User_Identity.exception.InvalidTokenException;
import escuelaing.edu.co.truckdar.User_Identity.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckdar.User_Identity.mapper.UserMapper;
import escuelaing.edu.co.truckdar.User_Identity.model.RefreshToken;
import escuelaing.edu.co.truckdar.User_Identity.model.Role;
import escuelaing.edu.co.truckdar.User_Identity.model.User;
import escuelaing.edu.co.truckdar.User_Identity.repository.RefreshTokenRepository;
import escuelaing.edu.co.truckdar.User_Identity.repository.UserRepository;
import escuelaing.edu.co.truckdar.User_Identity.security.JwtProvider;
import escuelaing.edu.co.truckdar.User_Identity.service.AuthService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Implementación de {@link AuthService} — registro, login, refresh y logout.
 */
@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final Set<Role> SELF_REGISTRABLE_ROLES = EnumSet.of(Role.CONDUCTOR, Role.COORDINADOR, Role.GENERADOR);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final UserMapper userMapper;
    private final DomainEventPublisher eventPublisher;

    public AuthServiceImpl(UserRepository userRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            PasswordEncoder passwordEncoder,
                            JwtProvider jwtProvider,
                            UserMapper userMapper,
                            DomainEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.userMapper = userMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        // El registro público crea conductores, coordinadores y generadores; ADMIN se otorga con PATCH /users/{id}/role.
        if (!SELF_REGISTRABLE_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException("El rol " + request.getRole()
                    + " no está permitido en el registro público; solo CONDUCTOR, COORDINADOR o GENERADOR");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Ya existe un usuario registrado con el email: " + request.getEmail());
        }

        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);

        // Publicar evento de dominio
        eventPublisher.publish(new UserRegisteredEvent(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                Instant.now().toEpochMilli()
        ));

        return generateAuthResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return generateAuthResponse(user);
    }

    @Override
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

        // Revocar el refresh token usado (rotación de tokens)
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        return generateAuthResponse(user);
    }

    @Override
    public MessageResponse logout(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        return MessageResponse.builder()
                .message("Sesión cerrada exitosamente")
                .build();
    }

    // ──────────────────────────── helpers ────────────────────────────

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
