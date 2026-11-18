package escuelaing.edu.co.truckcar.User_Identity.controller;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.ForgotPasswordRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.ResetPasswordRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.VerifyResetCodeRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.ForgotPasswordResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.MessageResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.ResetTokenResponse;
import escuelaing.edu.co.truckcar.User_Identity.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Recuperación de contraseña con un código al correo. Público (bajo /api/v1/auth/**), con límite de intentos en el gateway. */
@RestController
@RequestMapping("/api/v1/auth/password")
@Tag(name = "Recuperar contraseña", description = "Código de 6 dígitos al correo, verificación y contraseña nueva")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/forgot")
    @Operation(summary = "Envía un código al correo si tiene una cuenta (la respuesta es la misma en ambos casos)")
    public ResponseEntity<ForgotPasswordResponse> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        service.requestCode(request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ForgotPasswordResponse(
                "Si el correo está registrado, le enviamos un código de 6 dígitos. Revise también la carpeta de spam.",
                PasswordResetService.CODE_TTL.toSeconds(), PasswordResetService.RESEND_AFTER.toSeconds()));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verifica el código y devuelve un token de un solo uso para la contraseña nueva")
    public ResponseEntity<ResetTokenResponse> verify(@Valid @RequestBody VerifyResetCodeRequest request) {
        String token = service.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResetTokenResponse(token, PasswordResetService.TOKEN_TTL.toSeconds()));
    }

    @PostMapping("/reset")
    @Operation(summary = "Fija la contraseña nueva y cierra todas las sesiones abiertas")
    public ResponseEntity<MessageResponse> reset(@Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(request.getResetToken(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Contraseña actualizada. Inicie sesión con la contraseña nueva."));
    }
}
