package escuelaing.edu.co.truckcar.User_Identity.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.TreeMap;

/**
 * Manejo centralizado de excepciones en formato RFC 7807 (ver {@link Problems}): type, title, status, detail e
 * instance, más los campos de antes (error, message, path, timestamp) y errors[]/details en los 400 de validación.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND, "not-found", "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.CONFLICT, "duplicate-resource", "Recurso duplicado", ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.UNAUTHORIZED, "invalid-token", "Token inválido", ex.getMessage());
    }

    @ExceptionHandler(TwoFactorException.class)
    public ResponseEntity<ProblemDetail> handleTwoFactor(TwoFactorException ex, HttpServletRequest request) {
        return respond(request, ex.getStatus(), ex.getType(), ex.getTitle(), ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.UNAUTHORIZED, "invalid-credentials", "Credenciales inválidas",
                "Credenciales inválidas");
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ProblemDetail> handleSuspended(AccountSuspendedException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.FORBIDDEN, "account-suspended", "Cuenta suspendida", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.FORBIDDEN, "forbidden", "Acceso denegado", "Acceso denegado");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new TreeMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String detail = fieldErrors.isEmpty() ? "La solicitud tiene datos inválidos" : String.join("; ", fieldErrors.values());
        ResponseEntity<ProblemDetail> response = respond(request, HttpStatus.BAD_REQUEST, "validation-error",
                "Validación de datos fallida", detail);
        response.getBody().setProperty("details", fieldErrors);
        response.getBody().setProperty("errors", fieldErrors.entrySet().stream()
                .map(e -> Map.of("field", e.getKey(), "message", e.getValue())).toList());
        return response;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "bad-request", "Solicitud inválida", ex.getMessage());
    }

    /** JSON mal formado, UTF-8 inválido o un valor que no corresponde al tipo (rol desconocido): error del cliente. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Cuerpo ilegible en {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(request, HttpStatus.BAD_REQUEST, "unreadable-body", "Cuerpo de la solicitud inválido",
                "El cuerpo debe ser JSON válido en UTF-8 con los campos y valores esperados");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "invalid-parameter", "Parámetro inválido",
                ex.getName() + ": valor con un tipo incorrecto");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse errorResponse
                && errorResponse.getStatusCode().is4xxClientError()) {
            return respond(request, errorResponse.getStatusCode(), "bad-request", "Solicitud inválida",
                    errorResponse.getBody().getDetail());
        }
        log.error("Error no manejado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Error interno",
                "Error interno del servidor");
    }

    private static ResponseEntity<ProblemDetail> respond(HttpServletRequest request, HttpStatusCode status, String type,
                                                         String title, String detail) {
        return ResponseEntity.status(status).contentType(Problems.negotiatedType(request))
                .body(Problems.of(status, type, title, detail, request.getRequestURI()));
    }
}
