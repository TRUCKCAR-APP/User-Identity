package escuelaing.edu.co.truckcar.User_Identity.security;

import escuelaing.edu.co.truckcar.User_Identity.exception.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 en formato RFC 7807 (igual que el resto de errores) cuando una petición sin JWT válido llega a un recurso
 * protegido.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Problems.write(request, response, Problems.of(HttpStatus.UNAUTHORIZED, "unauthenticated", "No autenticado",
                "No autenticado — se requiere un token JWT válido", request.getRequestURI()));
    }
}
