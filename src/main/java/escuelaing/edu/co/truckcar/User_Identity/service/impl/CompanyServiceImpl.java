package escuelaing.edu.co.truckcar.User_Identity.service.impl;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.AffiliationChangeRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.AffiliationDecisionRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.CompanyDriverRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.RegisterRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.CompanyCatalogItem;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.CompanyResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.UserResponse;
import escuelaing.edu.co.truckcar.User_Identity.event.DomainEventPublisher;
import escuelaing.edu.co.truckcar.User_Identity.event.UserAffiliationChangedEvent;
import escuelaing.edu.co.truckcar.User_Identity.event.UserRegisteredEvent;
import escuelaing.edu.co.truckcar.User_Identity.exception.DuplicateResourceException;
import escuelaing.edu.co.truckcar.User_Identity.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckcar.User_Identity.mapper.UserMapper;
import escuelaing.edu.co.truckcar.User_Identity.model.AffiliationStatus;
import escuelaing.edu.co.truckcar.User_Identity.model.Company;
import escuelaing.edu.co.truckcar.User_Identity.model.DriverType;
import escuelaing.edu.co.truckcar.User_Identity.model.Role;
import escuelaing.edu.co.truckcar.User_Identity.model.User;
import escuelaing.edu.co.truckcar.User_Identity.model.UserStatus;
import escuelaing.edu.co.truckcar.User_Identity.repository.CompanyRepository;
import escuelaing.edu.co.truckcar.User_Identity.repository.UserRepository;
import escuelaing.edu.co.truckcar.User_Identity.service.CompanyService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final DomainEventPublisher eventPublisher;

    public CompanyServiceImpl(CompanyRepository companyRepository,
                              UserRepository userRepository,
                              UserMapper userMapper,
                              PasswordEncoder passwordEncoder,
                              DomainEventPublisher eventPublisher) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    // ── Registro ────────────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public void validateRegistration(Role role, RegisterRequest request) {
        if (role == Role.TRANSPORTADORA) {
            if (isBlank(request.getCompanyName())) {
                throw new IllegalArgumentException("El nombre de la empresa es obligatorio");
            }
            String nit = normalizeNit(request.getCompanyNit());
            if (companyRepository.existsByNit(nit)) {
                throw new DuplicateResourceException("Ya existe una empresa registrada con el NIT " + nit);
            }
        } else if (!isBlank(request.getCompanyName()) || !isBlank(request.getCompanyNit())) {
            throw new IllegalArgumentException("Los datos de empresa solo aplican a una transportadora");
        }
        if (role != Role.CONDUCTOR && (request.getDriverType() != null || request.getCompanyId() != null)) {
            throw new IllegalArgumentException("El tipo de conductor solo aplica a un conductor");
        }
        if (role == Role.CONDUCTOR) {
            requireConsistentChoice(request.getDriverType(), request.getCompanyId());
        }
    }

    @Override
    public void applyDriverRegistration(User user, RegisterRequest request) {
        if (request.getDriverType() != null) {
            assign(user, request.getDriverType(), request.getCompanyId(), AffiliationStatus.PENDIENTE);
        }
    }

    @Override
    @Transactional
    public void createCompanyFor(User owner, RegisterRequest request) {
        companyRepository.save(Company.builder()
                .name(request.getCompanyName().trim())
                .nit(normalizeNit(request.getCompanyNit()))
                .phoneNumber(owner.getPhoneNumber())
                .ownerUserId(owner.getId())
                .build());
    }

    @Override
    public void publishAffiliation(User driver) {
        if (driver.getDriverType() == null) {
            return;
        }
        String companyName = driver.getCompanyId() == null ? null
                : companyRepository.findById(driver.getCompanyId()).map(Company::getName).orElse(null);
        eventPublisher.publish(new UserAffiliationChangedEvent(
                driver.getId(),
                driver.getDriverType().name(),
                driver.getCompanyId(),
                companyName,
                driver.getAffiliationStatus() == null ? null : driver.getAffiliationStatus().name(),
                driver.affiliationAllowsLoads(),
                Instant.now().toEpochMilli()));
    }

    // ── Catálogo y consultas ────────────────────────────────────────────────────────────────────────────────────

    @Override
    public List<CompanyCatalogItem> catalog() {
        return companyRepository.findAllByOrderByNameAsc().stream()
                .map(company -> new CompanyCatalogItem(company.getId(), company.getName()))
                .toList();
    }

    @Override
    public List<CompanyResponse> listCompanies() {
        return companyRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Override
    public CompanyResponse getMyCompany(UUID ownerUserId) {
        return toResponse(ownedCompany(ownerUserId));
    }

    @Override
    public List<UserResponse> listMyDrivers(UUID ownerUserId) {
        Company company = ownedCompany(ownerUserId);
        return userRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId()).stream()
                .map(user -> named(userMapper.toResponse(user), company.getName()))
                .toList();
    }

    @Override
    public void fillCompanyNames(List<UserResponse> users) {
        List<UUID> ids = users.stream().map(UserResponse::getCompanyId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<UUID, String> names = companyRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName, (a, b) -> a));
        users.forEach(user -> {
            if (user.getCompanyId() != null) {
                user.setCompanyName(names.get(user.getCompanyId()));
            }
        });
    }

    private UserResponse withCompanyName(UserResponse user) {
        fillCompanyNames(List.of(user));
        return user;
    }

    // ── Gestión de la transportadora ────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse registerDriver(UUID ownerUserId, CompanyDriverRequest request) {
        Company company = ownedCompany(ownerUserId);
        String email = request.getEmail().trim();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("El correo ya se encuentra registrado: " + email);
        }
        User driver = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phoneNumber(blankToNull(request.getPhoneNumber()))
                .role(Role.CONDUCTOR)
                // La plataforma verifica la cuenta (ADMIN) igual que a cualquier conductor; la empresa responde por
                // la afiliación, que nace ACTIVA porque la registró ella.
                .status(UserStatus.PENDING_VERIFICATION)
                .driverType(DriverType.EMPRESA)
                .companyId(company.getId())
                .affiliationStatus(AffiliationStatus.ACTIVA)
                .build();
        User saved = userRepository.save(driver);
        eventPublisher.publish(new UserRegisteredEvent(saved.getId(), saved.getEmail(), saved.getRole().name(),
                Instant.now().toEpochMilli()));
        publishAffiliation(saved);
        return named(userMapper.toResponse(saved), company.getName());
    }

    @Override
    @Transactional
    public UserResponse decideAffiliation(UUID ownerUserId, UUID driverId, AffiliationDecisionRequest request) {
        Company company = ownedCompany(ownerUserId);
        AffiliationStatus decision = request.getStatus();
        if (decision != AffiliationStatus.ACTIVA && decision != AffiliationStatus.RECHAZADA) {
            throw new IllegalArgumentException("La respuesta a una afiliación es ACTIVA o RECHAZADA");
        }
        User driver = driverOf(company, driverId);
        if (driver.getAffiliationStatus() == decision) {
            return named(userMapper.toResponse(driver), company.getName());
        }
        driver.setAffiliationStatus(decision);
        User saved = userRepository.save(driver);
        publishAffiliation(saved);
        return named(userMapper.toResponse(saved), company.getName());
    }

    @Override
    @Transactional
    public UserResponse unlinkDriver(UUID ownerUserId, UUID driverId) {
        Company company = ownedCompany(ownerUserId);
        User driver = driverOf(company, driverId);
        assign(driver, DriverType.INDEPENDIENTE, null, null);
        User saved = userRepository.save(driver);
        publishAffiliation(saved);
        return userMapper.toResponse(saved);
    }

    // ── El conductor ────────────────────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse changeMyAffiliation(UUID driverId, AffiliationChangeRequest request) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + driverId));
        if (driver.getRole() != Role.CONDUCTOR) {
            throw new AccessDeniedException("Solo un conductor elige si es independiente o de empresa");
        }
        requireConsistentChoice(request.getDriverType(), request.getCompanyId());
        boolean sameCompany = request.getDriverType() == DriverType.EMPRESA && driver.getDriverType() == DriverType.EMPRESA
                && request.getCompanyId().equals(driver.getCompanyId());
        boolean unchanged = request.getDriverType() == DriverType.INDEPENDIENTE
                ? driver.getDriverType() == DriverType.INDEPENDIENTE
                : sameCompany && driver.getAffiliationStatus() != AffiliationStatus.RECHAZADA;
        if (unchanged) {
            return withCompanyName(userMapper.toResponse(driver));
        }
        // Pedir otra vez la afiliación a la misma empresa tras un rechazo la deja PENDIENTE de nuevo.
        assign(driver, request.getDriverType(), request.getCompanyId(), AffiliationStatus.PENDIENTE);
        User saved = userRepository.save(driver);
        publishAffiliation(saved);
        return withCompanyName(userMapper.toResponse(saved));
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────────────────────

    private void assign(User user, DriverType type, UUID companyId, AffiliationStatus status) {
        user.setDriverType(type);
        if (type == DriverType.EMPRESA) {
            if (!companyRepository.existsById(companyId)) {
                throw new IllegalArgumentException("La empresa seleccionada no existe");
            }
            user.setCompanyId(companyId);
            user.setAffiliationStatus(status);
        } else {
            user.setCompanyId(null);
            user.setAffiliationStatus(null);
        }
    }

    private static void requireConsistentChoice(DriverType type, UUID companyId) {
        if (type == DriverType.EMPRESA && companyId == null) {
            throw new IllegalArgumentException("Elija la empresa transportadora a la que pertenece");
        }
        if (type != DriverType.EMPRESA && companyId != null) {
            throw new IllegalArgumentException("Solo un conductor de empresa indica una transportadora");
        }
    }

    private Company ownedCompany(UUID ownerUserId) {
        return companyRepository.findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Esta cuenta no tiene una empresa transportadora"));
    }

    /** Conductor afiliado o con solicitud a la empresa; otro conductor no existe para ella (404, no 403). */
    private User driverOf(Company company, UUID driverId) {
        return userRepository.findById(driverId)
                .filter(user -> user.getRole() == Role.CONDUCTOR && company.getId().equals(user.getCompanyId()))
                .orElseThrow(() -> new ResourceNotFoundException("El conductor no pertenece a esta empresa"));
    }

    private CompanyResponse toResponse(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .nit(company.getNit())
                .phoneNumber(company.getPhoneNumber())
                .ownerUserId(company.getOwnerUserId())
                .createdAt(company.getCreatedAt())
                .activeDrivers(userRepository.countByCompanyIdAndAffiliationStatus(company.getId(), AffiliationStatus.ACTIVA))
                .pendingRequests(userRepository.countByCompanyIdAndAffiliationStatus(company.getId(), AffiliationStatus.PENDIENTE))
                .build();
    }

    private static UserResponse named(UserResponse response, String companyName) {
        response.setCompanyName(companyName);
        return response;
    }

    /** NIT: solo dígitos; con guion, sin el dígito de verificación (900.123.456-7 → 900123456). */
    static String normalizeNit(String raw) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("El NIT de la empresa es obligatorio");
        }
        String base = raw.contains("-") ? raw.substring(0, raw.indexOf('-')) : raw;
        String digits = base.replaceAll("\\D", "");
        if (digits.length() < 6 || digits.length() > 15) {
            throw new IllegalArgumentException("El NIT debe tener entre 6 y 15 dígitos");
        }
        return digits;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
