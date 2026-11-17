package escuelaing.edu.co.truckcar.User_Identity.controller;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.AffiliationChangeRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.AffiliationDecisionRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.CompanyDriverRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.CompanyCatalogItem;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.CompanyResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.UserResponse;
import escuelaing.edu.co.truckcar.User_Identity.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Empresas transportadoras y afiliación de conductores.
 * <ul>
 *   <li>{@code GET /api/v1/auth/companies}: catálogo público para el registro (id y nombre).</li>
 *   <li>{@code /api/v1/companies/me/**}: la transportadora gestiona a sus conductores.</li>
 *   <li>{@code GET /api/v1/companies}: listado para ADMIN y COORDINADOR.</li>
 *   <li>{@code PUT /api/v1/users/me/affiliation}: el conductor elige si es independiente o de empresa.</li>
 * </ul>
 */
@RestController
@Tag(name = "Empresas", description = "Transportadoras y afiliación de conductores")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping("/api/v1/auth/companies")
    @Operation(summary = "Transportadoras disponibles para el registro de un conductor de empresa")
    public ResponseEntity<List<CompanyCatalogItem>> catalog() {
        return ResponseEntity.ok(companyService.catalog());
    }

    @GetMapping("/api/v1/companies")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR')")
    @Operation(summary = "Transportadoras con sus conductores activos y solicitudes pendientes")
    public ResponseEntity<List<CompanyResponse>> listCompanies() {
        return ResponseEntity.ok(companyService.listCompanies());
    }

    @GetMapping("/api/v1/companies/me")
    @PreAuthorize("hasRole('TRANSPORTADORA')")
    @Operation(summary = "Empresa de la transportadora autenticada")
    public ResponseEntity<CompanyResponse> myCompany(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(companyService.getMyCompany(userId));
    }

    @GetMapping("/api/v1/companies/me/drivers")
    @PreAuthorize("hasRole('TRANSPORTADORA')")
    @Operation(summary = "Conductores afiliados y solicitudes de afiliación de la empresa")
    public ResponseEntity<List<UserResponse>> myDrivers(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(companyService.listMyDrivers(userId));
    }

    @PostMapping("/api/v1/companies/me/drivers")
    @PreAuthorize("hasRole('TRANSPORTADORA')")
    @Operation(summary = "Registrar un conductor de la empresa (nace con la afiliación ACTIVA)")
    public ResponseEntity<UserResponse> registerDriver(@AuthenticationPrincipal UUID userId,
                                                       @Valid @RequestBody CompanyDriverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.registerDriver(userId, request));
    }

    @PatchMapping("/api/v1/companies/me/drivers/{driverId}/affiliation")
    @PreAuthorize("hasRole('TRANSPORTADORA')")
    @Operation(summary = "Aceptar (ACTIVA) o rechazar (RECHAZADA) una solicitud de afiliación")
    public ResponseEntity<UserResponse> decide(@AuthenticationPrincipal UUID userId, @PathVariable UUID driverId,
                                               @Valid @RequestBody AffiliationDecisionRequest request) {
        return ResponseEntity.ok(companyService.decideAffiliation(userId, driverId, request));
    }

    @DeleteMapping("/api/v1/companies/me/drivers/{driverId}")
    @PreAuthorize("hasRole('TRANSPORTADORA')")
    @Operation(summary = "Desvincular a un conductor: queda como independiente")
    public ResponseEntity<UserResponse> unlink(@AuthenticationPrincipal UUID userId, @PathVariable UUID driverId) {
        return ResponseEntity.ok(companyService.unlinkDriver(userId, driverId));
    }

    @PutMapping("/api/v1/users/me/affiliation")
    @PreAuthorize("hasRole('CONDUCTOR')")
    @Operation(summary = "El conductor elige: independiente o de empresa (pide la afiliación)")
    public ResponseEntity<UserResponse> changeMyAffiliation(@AuthenticationPrincipal UUID userId,
                                                            @Valid @RequestBody AffiliationChangeRequest request) {
        return ResponseEntity.ok(companyService.changeMyAffiliation(userId, request));
    }
}
