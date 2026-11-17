package escuelaing.edu.co.truckcar.User_Identity.service;

import escuelaing.edu.co.truckcar.User_Identity.dto.request.TwoFactorDisableRequest;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.MessageResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.TwoFactorBackupCodesResponse;
import escuelaing.edu.co.truckcar.User_Identity.dto.response.TwoFactorSetupResponse;
import escuelaing.edu.co.truckcar.User_Identity.model.User;

import java.util.UUID;

/** Doble autenticación opcional con TOTP (RFC 6238) y códigos de respaldo de un solo uso. */
public interface TwoFactorService {

    /** Secreto nuevo (pendiente) con su URI otpauth:// y QR. No activa el 2FA. */
    TwoFactorSetupResponse setup(UUID userId);

    /** Confirma el secreto pendiente con un código y activa el 2FA. Devuelve los códigos de respaldo una sola vez. */
    TwoFactorBackupCodesResponse verifySetup(UUID userId, String code);

    /** Desactiva el 2FA con la contraseña o un código vigente. */
    MessageResponse disable(UUID userId, TwoFactorDisableRequest request);

    /**
     * Valida el código del segundo paso del login (TOTP o de respaldo, que se consume). Cuenta los fallos y bloquea
     * temporalmente tras varios seguidos. Lanza TwoFactorException si no es válido.
     */
    void verifyLoginCode(User user, String code);
}
