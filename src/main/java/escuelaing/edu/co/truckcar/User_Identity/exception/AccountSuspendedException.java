package escuelaing.edu.co.truckcar.User_Identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Lanzada cuando una cuenta SUSPENDED intenta iniciar sesión o renovar su token.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountSuspendedException extends RuntimeException {

    public AccountSuspendedException(String message) {
        super(message);
    }
}
