package escuelaing.edu.co.truckcar.User_Identity.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

/**
 * Errores RFC 7807 de User-Identity, iguales en el controlador (GlobalExceptionHandler) y en los filtros de seguridad
 * (401 y 403 de Spring Security, que no pasan por el @RestControllerAdvice).
 * <p>
 * Conserva como propiedades los campos del ErrorResponse anterior (error, message, path, timestamp) para no romper a
 * la PWA. Content-Type: application/problem+json solo para quien lo pide en Accept; application/json para el resto.
 */
public final class Problems {

    static final String TYPE_BASE = "https://truckcar.co/errors/";

    /** Para los filtros, que escriben la respuesta a mano: con el mixin, las propiedades van en la raíz. */
    private static final ObjectMapper WRITER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String type, String title, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + type));
        problem.setTitle(title);
        if (path != null) {
            problem.setInstance(URI.create(path));
            problem.setProperty("path", path);
        }
        problem.setProperty("error", title);
        problem.setProperty("message", detail);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    public static MediaType negotiatedType(HttpServletRequest request) {
        String accept = request == null ? null : request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON;
    }

    /** Escribe el problema en una respuesta de filtro (sin pasar por los message converters de Spring MVC). */
    public static void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
            throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(negotiatedType(request).toString());
        response.setCharacterEncoding("UTF-8");
        WRITER.writeValue(response.getOutputStream(), problem);
    }
}
