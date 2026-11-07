package escuelaing.edu.co.truckcar.User_Identity.service.impl;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.ChangeStatusRequest;
import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.event.UserStatusChangedEvent;
import escuelaing.edu.co.truckcar.User_Identity.mapper.UserMapper;
import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.repository.RefreshTokenRepository;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verificar o suspender una cuenta se anuncia como user.status.changed; fleets-drivers decide con eso el matching. */
@ExtendWith(MockitoExtension.class)
class UserStatusEventTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserMapper userMapper;
    @Mock private DomainEventPublisher eventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    private User driver(UserStatus status) {
        User user = User.builder().id(UUID.randomUUID()).email("conductor@truckcar.co").passwordHash("x")
                .firstName("Andrés").lastName("Rojas").role(Role.CONDUCTOR).status(status).build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return user;
    }

    private static ChangeStatusRequest to(UserStatus status) {
        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setStatus(status);
        return request;
    }

    @Test
    void verifyingAPendingDriverPublishesTheNewStatus() {
        User user = driver(UserStatus.PENDING_VERIFICATION);

        userService.changeStatus(user.getId(), to(UserStatus.ACTIVE));

        ArgumentCaptor<UserStatusChangedEvent> event = ArgumentCaptor.forClass(UserStatusChangedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(user.getId());
        assertThat(event.getValue().role()).isEqualTo("CONDUCTOR");
        assertThat(event.getValue().previousStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(event.getValue().status()).isEqualTo("ACTIVE");
    }

    @Test
    void suspendingADriverPublishesItAndRevokesItsSessions() {
        User user = driver(UserStatus.ACTIVE);

        userService.changeStatus(user.getId(), to(UserStatus.SUSPENDED));

        ArgumentCaptor<UserStatusChangedEvent> event = ArgumentCaptor.forClass(UserStatusChangedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().status()).isEqualTo("SUSPENDED");
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    }

    @Test
    void settingTheSameStatusPublishesNothing() {
        User user = driver(UserStatus.ACTIVE);

        userService.changeStatus(user.getId(), to(UserStatus.ACTIVE));

        verify(eventPublisher, never()).publish(any(UserStatusChangedEvent.class));
    }
}
