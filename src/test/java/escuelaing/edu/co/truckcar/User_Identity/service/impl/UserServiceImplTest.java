package escuelaing.edu.co.truckcar.User_Identity.service.impl;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.ChangeRoleRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.ChangeStatusRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.UpdateProfileRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.UserResponse;
import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.event.UserRoleChangedEvent;
import escuelaing.edu.co.truckcar.User_Identity.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckcar.User_Identity.mapper.UserMapper;
import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl — unit tests")
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private DomainEventPublisher eventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    private UUID userId;
    private User sampleUser;
    private UserResponse sampleResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .email("maria@truckcar.co")
                .passwordHash("$2a$10$hash")
                .firstName("María")
                .lastName("López")
                .role(Role.COORDINADOR)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sampleResponse = UserResponse.builder()
                .id(userId)
                .email("maria@truckcar.co")
                .firstName("María")
                .lastName("López")
                .role(Role.COORDINADOR)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("getProfile() — usuario existente devuelve UserResponse")
    void getProfile_success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userMapper.toResponse(sampleUser)).thenReturn(sampleResponse);

        UserResponse result = userService.getProfile(userId);

        assertThat(result.getEmail()).isEqualTo("maria@truckcar.co");
        assertThat(result.getRole()).isEqualTo(Role.COORDINADOR);
    }

    @Test
    @DisplayName("getProfile() — usuario inexistente lanza ResourceNotFoundException")
    void getProfile_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateProfile() — actualiza campos parciales")
    void updateProfile_success() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .firstName("María Camila")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(sampleResponse);

        UserResponse result = userService.updateProfile(userId, request);

        assertThat(result).isNotNull();
        verify(userMapper).updateEntityFromRequest(request, sampleUser);
    }

    @Test
    @DisplayName("changeStatus() — suspende un usuario")
    void changeStatus_suspend() {
        ChangeStatusRequest request = ChangeStatusRequest.builder()
                .status(UserStatus.SUSPENDED)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(sampleResponse);

        UserResponse result = userService.changeStatus(userId, request);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("changeRole() — cambia rol y publica evento")
    void changeRole_success() {
        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .role(Role.ADMIN)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(sampleResponse);

        UserResponse result = userService.changeRole(userId, request);

        assertThat(result).isNotNull();
        verify(eventPublisher).publish(any(UserRoleChangedEvent.class));
    }
}
