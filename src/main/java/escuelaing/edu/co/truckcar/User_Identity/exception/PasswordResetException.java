package escuelaing.edu.co.truckcar.User_Identity.exception;

import org.springframework.http.HttpStatus;

/** Error de la recuperación de contraseña con su estado y su tipo RFC 7807 (https://truckcar.co/errors/{type}). */
public class PasswordResetException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String title;

    public PasswordResetException(HttpStatus status, String type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    /** El mismo error para un correo sin cuenta, sin código pedido, vencido o equivocado: no revela si la cuenta existe. */
    public static PasswordResetException invalidCode() {
        return new PasswordResetException(HttpStatus.BAD_REQUEST, "invalid-reset-code", "Código inválido",
                "El código no es válido o ya venció. Revise el correo o pida un código nuevo");
    }

    public static PasswordResetException tooManyAttempts() {
        return new PasswordResetException(HttpStatus.TOO_MANY_REQUESTS, "reset-code-locked", "Demasiados intentos",
                "Se superó el número de intentos con este código. Pida un código nuevo");
    }

    public static PasswordResetException invalidToken() {
        return new PasswordResetException(HttpStatus.BAD_REQUEST, "invalid-reset-token", "Solicitud vencida",
                "La verificación venció o ya se usó. Empiece de nuevo con «¿Olvidó su contraseña?»");
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
