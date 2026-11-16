package escuelaing.edu.co.truckcar.User_Identity.security;

import escuelaing.edu.co.truckcar.User_Identity.exception.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 en formato RFC 7807 cuando una regla de autorización por URL rechaza a un usuario autenticado. Sin este handler
 * Spring Security respondía 403 sin cuerpo y la PWA no tenía mensaje que mostrar.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Problems.write(request, response, Problems.of(HttpStatus.FORBIDDEN, "forbidden", "Acceso denegado",
                "No tiene permisos para esta operación", request.getRequestURI()));
    }
}
