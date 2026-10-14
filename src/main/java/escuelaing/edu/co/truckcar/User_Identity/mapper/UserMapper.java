package escuelaing.edu.co.truckcar.User_Identity.mapper;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.RegisterRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.UserResponse;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper — convierte entre entidad {@link User} y DTOs.
 * La implementación se genera en tiempo de compilación.
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {

    /**
     * Convierte un {@link RegisterRequest} a una entidad {@link User}.
     * {@code passwordHash} se asigna aparte en el servicio (tras aplicar BCrypt).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    User toEntity(RegisterRequest request);

    /**
     * Convierte una entidad {@link User} a {@link UserResponse}.
     */
    UserResponse toResponse(User user);

    /**
     * Actualiza campos no-nulos de {@code request} sobre la entidad existente.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    void updateEntityFromRequest(escuelaing.edu.co.truckcar.User_Identity.dto.request.UpdateProfileRequest request,
                                  @MappingTarget User user);
}
