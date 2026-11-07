package escuelaing.edu.co.truckcar.User_Identity.service.impl;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.ChangeRoleRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.ChangeStatusRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.UpdateProfileRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.UserResponse;
import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.event.UserRoleChangedEvent;
import escuelaing.edu.co.truckcar.User_Identity.event.UserStatusChangedEvent;
import escuelaing.edu.co.truckcar.User_Identity.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckcar.User_Identity.mapper.UserMapper;
import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.repository.RefreshTokenRepository;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import escuelaing.edu.co.truckcar.User_Identity.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Implementación de {@link UserService} — consulta y gestión de perfiles de usuario.
 */
@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final DomainEventPublisher eventPublisher;

    public UserServiceImpl(UserRepository userRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            UserMapper userMapper,
                            DomainEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userMapper = userMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UserResponse getProfile(UUID userId) {
        return userMapper.toResponse(findUserOrThrow(userId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);
        userMapper.updateEntityFromRequest(request, user);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse getUserById(UUID id) {
        return userMapper.toResponse(findUserOrThrow(id));
    }

    @Override
    public Page<UserResponse> listUsers(Role role, UserStatus status, Pageable pageable) {
        return userRepository.findAllWithFilters(role, status, pageable)
                .map(userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse changeStatus(UUID id, ChangeStatusRequest request) {
        User user = findUserOrThrow(id);
        if (request.getStatus() == UserStatus.SUSPENDED) {
            requireAnotherActiveAdmin(user);
        }
        UserStatus previous = user.getStatus();
        user.setStatus(request.getStatus());
        User saved = userRepository.save(user);
        if (saved.getStatus() == UserStatus.SUSPENDED) {
            // Sin refresh tokens la sesión termina al vencer el access token (15 min).
            refreshTokenRepository.revokeAllByUserId(saved.getId());
        }
        if (previous != saved.getStatus()) {
            // Verificar (ACTIVE) o suspender cambia si el conductor puede recibir cargas: fleets-drivers lo escucha.
            eventPublisher.publish(new UserStatusChangedEvent(
                    saved.getId(),
                    saved.getRole().name(),
                    previous == null ? null : previous.name(),
                    saved.getStatus().name(),
                    Instant.now().toEpochMilli()
            ));
        }
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse changeRole(UUID id, ChangeRoleRequest request) {
        User user = findUserOrThrow(id);
        if (user.getRole() == request.getRole()) {
            return userMapper.toResponse(user);
        }
        if (request.getRole() != Role.ADMIN) {
            requireAnotherActiveAdmin(user);
        }
        String oldRole = user.getRole().name();
        user.setRole(request.getRole());
        User saved = userRepository.save(user);
        // El rol viaja en el JWT: se revocan los refresh tokens para que el rol anterior deje de valer en 15 min y
        // la próxima sesión ya lleve el nuevo.
        refreshTokenRepository.revokeAllByUserId(saved.getId());

        // Publicar evento de cambio de rol
        eventPublisher.publish(new UserRoleChangedEvent(
                saved.getId(),
                oldRole,
                saved.getRole().name(),
                Instant.now().toEpochMilli()
        ));

        return userMapper.toResponse(saved);
    }

    /** Quitar el rol o suspender al único ADMIN activo dejaría la plataforma sin administración. */
    private void requireAnotherActiveAdmin(User user) {
        if (user.getRole() == Role.ADMIN && user.getStatus() == UserStatus.ACTIVE
                && userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new IllegalArgumentException("No se puede degradar ni suspender al único administrador activo");
        }
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado con id: " + id));
    }
}
