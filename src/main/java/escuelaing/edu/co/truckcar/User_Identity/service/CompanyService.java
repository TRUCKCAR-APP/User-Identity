package escuelaing.edu.co.truckcar.User_Identity.service;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.AffiliationChangeRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.AffiliationDecisionRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.CompanyDriverRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.RegisterRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.CompanyCatalogItem;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.CompanyResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.UserResponse;
import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;

import java.util.List;
import java.util.UUID;

/**
 * Empresas transportadoras y afiliación de conductores (independiente o de empresa).
 */
public interface CompanyService {

    // ── Registro (lo usa AuthServiceImpl) ──

    /** Valida los datos de empresa o afiliación del registro antes de crear el usuario. */
    void validateRegistration(Role role, RegisterRequest request);

    /** Aplica el tipo de conductor del registro al usuario aún sin guardar. */
    void applyDriverRegistration(User user, RegisterRequest request);

    /** Crea la empresa del usuario TRANSPORTADORA recién guardado. */
    void createCompanyFor(User owner, RegisterRequest request);

    /** Publica user.affiliation.changed para un conductor con tipo (no hace nada si no lo tiene). */
    void publishAffiliation(User driver);

    // ── Catálogo y consultas ──

    List<CompanyCatalogItem> catalog();

    List<CompanyResponse> listCompanies();

    CompanyResponse getMyCompany(UUID ownerUserId);

    List<UserResponse> listMyDrivers(UUID ownerUserId);

    /** Completa companyName en las respuestas que tienen companyId. */
    void fillCompanyNames(List<UserResponse> users);

    // ── Gestión de la transportadora ──

    UserResponse registerDriver(UUID ownerUserId, CompanyDriverRequest request);

    UserResponse decideAffiliation(UUID ownerUserId, UUID driverId, AffiliationDecisionRequest request);

    UserResponse unlinkDriver(UUID ownerUserId, UUID driverId);

    // ── El conductor ──

    UserResponse changeMyAffiliation(UUID driverId, AffiliationChangeRequest request);
}
