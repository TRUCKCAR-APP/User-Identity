package escuelaing.edu.co.truckcar.User_Identity.exception;

import org.springframework.http.HttpStatus;

/** Error de la doble autenticación con su estado y su tipo RFC 7807 (https://truckcar.co/errors/{type}). */
public class TwoFactorException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String title;

    public TwoFactorException(HttpStatus status, String type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    public static TwoFactorException invalidCode() {
        return new TwoFactorException(HttpStatus.UNAUTHORIZED, "invalid-2fa-code", "Código de verificación inválido",
                "El código no es válido o ya se usó. Revise la hora del teléfono o use un código de respaldo");
    }

    public static TwoFactorException invalidPendingToken() {
        return new TwoFactorException(HttpStatus.UNAUTHORIZED, "invalid-2fa-token", "Verificación vencida",
                "La verificación en dos pasos venció o no es válida; inicie sesión de nuevo");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }
}
