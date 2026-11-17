package escuelaing.edu.co.truckcar.User_Identity.service;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.LoginRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.RefreshTokenRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.request.RegisterRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.AuthResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.MessageResponse;

import java.util.UUID;

/**
 * Contrato de lógica de negocio para autenticación y gestión de tokens.
 */
public interface AuthService {

    AuthResponse register(RegisterRequest request);

    /** Con 2FA activo devuelve requiresTwoFactor y un token temporal en lugar de la sesión. */
    AuthResponse login(LoginRequest request);

    /** Segundo paso del login con 2FA. */
    AuthResponse verifyTwoFactorLogin(escuelaing.edu.co.truckcar.User_Identity.dto.request.TwoFactorLoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    MessageResponse logout(UUID userId);
}
