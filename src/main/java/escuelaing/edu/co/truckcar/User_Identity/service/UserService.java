package escuelaing.edu.co.truckcar.User_Identity.service;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.ChangeRoleRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.ChangeStatusRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.UpdateProfileRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.UserResponse;
import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Contrato de lógica de negocio para gestión de usuarios.
 */
public interface UserService {

    UserResponse getProfile(UUID userId);

    UserResponse updateProfile(UUID userId, UpdateProfileRequest request);

    UserResponse getUserById(UUID id);

    Page<UserResponse> listUsers(Role role, UserStatus status, Pageable pageable);

    UserResponse changeStatus(UUID id, ChangeStatusRequest request);

    UserResponse changeRole(UUID id, ChangeRoleRequest request);
}
