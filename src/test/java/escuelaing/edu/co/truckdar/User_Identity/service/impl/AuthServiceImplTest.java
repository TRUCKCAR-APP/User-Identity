package escuelaing.edu.co.truckdar.User_Identity.service.impl;

import escuelaing.edu.co.truckdar.User_Identity.dto.request.LoginRequest;
import escuelaing.edu.co.truckdar.User_Identity.dto.request.RefreshTokenRequest;
import escuelaing.edu.co.truckdar.User_Identity.dto.request.RegisterRequest;
import escuelaing.edu.co.truckdar.User_Identity.dto.response.AuthResponse;
import escuelaing.edu.co.truckdar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckdar.User_Identity.exception.DuplicateResourceException;
import escuelaing.edu.co.truckdar.User_Identity.exception.InvalidTokenException;
import escuelaing.edu.co.truckdar.User_Identity.mapper.UserMapper;
import escuelaing.edu.co.truckdar.User_Identity.model.RefreshToken;
import escuelaing.edu.co.truckdar.User_Identity.model.Role;
import escuelaing.edu.co.truckdar.User_Identity.model.User;
import escuelaing.edu.co.truckdar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckdar.User_Identity.repository.RefreshTokenRepository;
import escuelaing.edu.co.truckdar.User_Identity.repository.UserRepository;
import escuelaing.edu.co.truckdar.User_Identity.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl — unit tests")
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private UserMapper userMapper;
    @Mock private DomainEventPublisher eventPublisher;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .email("juan@truckdar.co")
                .passwordHash("$2a$10$hashedvalue")
                .firstName("Juan")
                .lastName("Pérez")
                .role(Role.CONDUCTOR)
                .status(UserStatus.PENDING_VERIFICATION)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("register() — registro exitoso devuelve tokens")
    void register_success() {
        RegisterRequest request = RegisterRequest.builder()
                .email("juan@truckdar.co")
                .password("Segura123!")
                .firstName("Juan")
                .lastName("Pérez")
                .role(Role.CONDUCTOR)
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userMapper.toEntity(any(RegisterRequest.class))).thenReturn(sampleUser);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedvalue");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtProvider.generateAccessToken(any(), anyString(), anyString()))
                .thenReturn("access-token");
        when(jwtProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any())).thenReturn(RefreshToken.builder().build());

        AuthResponse response = authService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        verify(eventPublisher).publish(any(escuelaing.edu.co.truckdar.User_Identity.event.UserRegisteredEvent.class));
    }

    @ParameterizedTest(name = "register() — el registro público acepta el rol {0}")
    @EnumSource(value = Role.class, names = {"CONDUCTOR", "COORDINADOR", "GENERADOR"})
    void register_publicRolesAreAccepted(Role role) {
        RegisterRequest request = RegisterRequest.builder()
                .email(role.name().toLowerCase() + "@truckdar.co")
                .password("Segura123!")
                .firstName("Usuario")
                .lastName(role.name())
                .role(role)
                .build();
        sampleUser.setRole(role);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userMapper.toEntity(any(RegisterRequest.class))).thenReturn(sampleUser);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedvalue");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtProvider.generateAccessToken(any(), anyString(), anyString())).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any())).thenReturn(RefreshToken.builder().build());

        assertThat(authService.register(request).getAccessToken()).isEqualTo("access-token");
        // El token lleva el rol registrado: el gateway lo propaga en X-Auth-Role.
        verify(jwtProvider).generateAccessToken(any(), anyString(), org.mockito.ArgumentMatchers.eq(role.name()));
    }

    @Test
    @DisplayName("register() — email duplicado lanza DuplicateResourceException")
    void register_duplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("juan@truckdar.co")
                .password("Segura123!")
                .firstName("Juan")
                .lastName("Pérez")
                .role(Role.CONDUCTOR)
                .build();

        when(userRepository.existsByEmail("juan@truckdar.co")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("juan@truckdar.co");
    }

    @Test
    @DisplayName("register() — el registro público no permite autoasignarse ADMIN")
    void register_adminRoleIsRejected() {
        RegisterRequest request = RegisterRequest.builder()
                .email("intruso@truckdar.co")
                .password("Segura123!")
                .firstName("Intruso")
                .lastName("Admin")
                .role(Role.ADMIN)
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADMIN");
        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
        verify(eventPublisher, org.mockito.Mockito.never())
                .publish(any(escuelaing.edu.co.truckdar.User_Identity.event.UserRegisteredEvent.class));
    }

    @Test
    @DisplayName("login() — credenciales válidas devuelve tokens")
    void login_success() {
        LoginRequest request = LoginRequest.builder()
                .email("juan@truckdar.co")
                .password("Segura123!")
                .build();

        when(userRepository.findByEmail("juan@truckdar.co"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Segura123!", sampleUser.getPasswordHash()))
                .thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtProvider.generateAccessToken(any(), anyString(), anyString()))
                .thenReturn("access-token");
        when(jwtProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtProvider.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any())).thenReturn(RefreshToken.builder().build());

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("login() — contraseña incorrecta lanza BadCredentialsException")
    void login_wrongPassword() {
        LoginRequest request = LoginRequest.builder()
                .email("juan@truckdar.co")
                .password("WrongPass!")
                .build();

        when(userRepository.findByEmail("juan@truckdar.co"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPass!", sampleUser.getPasswordHash()))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("refresh() — token inválido lanza InvalidTokenException")
    void refresh_invalidToken() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("invalid-token")
                .build();

        when(refreshTokenRepository.findByTokenAndRevokedFalse("invalid-token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(InvalidTokenException.class);
    }
}
