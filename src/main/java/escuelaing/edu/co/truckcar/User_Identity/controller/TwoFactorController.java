package escuelaing.edu.co.truckcar.User_Identity.controller;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.TwoFactorCodeRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.TwoFactorDisableRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.TwoFactorLoginRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.AuthResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.MessageResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.TwoFactorBackupCodesResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.TwoFactorSetupResponse;
import escuelaing.edu.co.truckcar.User_Identity.service.AuthService;
import escuelaing.edu.co.truckcar.User_Identity.service.TwoFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Doble autenticación (TOTP). setup, verify-setup y disable exigen una sesión completa (SecurityConfig);
 * verify-login es público: lo autoriza el token temporal de /auth/login.
 */
@RestController
@RequestMapping("/api/v1/auth/2fa")
@Tag(name = "Doble autenticación", description = "TOTP (RFC 6238) con app autenticadora y códigos de respaldo")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;
    private final AuthService authService;

    public TwoFactorController(TwoFactorService twoFactorService, AuthService authService) {
        this.twoFactorService = twoFactorService;
        this.authService = authService;
    }

    @PostMapping("/setup")
    @Operation(summary = "Genera el secreto TOTP (clave, URI otpauth:// y QR). No activa el 2FA")
    public ResponseEntity<TwoFactorSetupResponse> setup(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(twoFactorService.setup(userId));
    }

    @PostMapping("/verify-setup")
    @Operation(summary = "Confirma el primer código, activa el 2FA y devuelve los códigos de respaldo (una sola vez)")
    public ResponseEntity<TwoFactorBackupCodesResponse> verifySetup(@AuthenticationPrincipal UUID userId,
                                                                    @Valid @RequestBody TwoFactorCodeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(twoFactorService.verifySetup(userId, request.getCode()));
    }

    @PostMapping("/disable")
    @Operation(summary = "Desactiva el 2FA con la contraseña o un código vigente")
    public ResponseEntity<MessageResponse> disable(@AuthenticationPrincipal UUID userId,
                                                   @RequestBody(required = false) TwoFactorDisableRequest request) {
        return ResponseEntity.ok(twoFactorService.disable(userId, request));
    }

    @PostMapping("/verify-login")
    @Operation(summary = "Segundo paso del login: token temporal + código TOTP o de respaldo → access y refresh token")
    public ResponseEntity<AuthResponse> verifyLogin(@Valid @RequestBody TwoFactorLoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.verifyTwoFactorLogin(request));
    }
}
