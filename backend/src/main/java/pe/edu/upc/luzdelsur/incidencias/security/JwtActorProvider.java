package pe.edu.upc.luzdelsur.incidencias.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import pe.edu.upc.luzdelsur.incidencias.auth.JwtService;
import pe.edu.upc.luzdelsur.incidencias.common.UnauthorizedException;
import pe.edu.upc.luzdelsur.incidencias.config.AppProperties;

/**
 * Proveedor de identidad principal.
 *
 * <ol>
 *   <li>Con {@code Authorization: Bearer <token>}: valida firma, emisor, tipo y
 *       vencimiento; el actor es {@code sub} + {@code role}. Un token invalido
 *       responde 401 (no se degrada a anonimo).</li>
 *   <li>Sin token y con {@code app.security.allow-dev-headers=true}: delega en
 *       {@link HeaderActorProvider} (pruebas locales y Swagger).</li>
 *   <li>Sin token en modo estricto: anonimo con rol CLIENTE. Solo alcanza para
 *       los endpoints publicos (alta y seguimiento por UUID).</li>
 * </ol>
 *
 * <p>Fuera de una peticion HTTP (semilla de datos, tareas internas) el actor es
 * SISTEMA.</p>
 */
@Primary
@Component
public class JwtActorProvider implements ActorProvider {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final HeaderActorProvider headerActorProvider;
    private final AppProperties properties;

    public JwtActorProvider(JwtService jwtService, HeaderActorProvider headerActorProvider,
                            AppProperties properties) {
        this.jwtService = jwtService;
        this.headerActorProvider = headerActorProvider;
        this.properties = properties;
    }

    @Override
    public Actor current() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return new Actor(Actor.ANONYMOUS_ID, ActorRole.SISTEMA);
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                throw new UnauthorizedException("Cabecera Authorization no valida: se espera 'Bearer <token>'");
            }
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                throw new UnauthorizedException("Token vacio");
            }
            return jwtService.parseAccess(token);
        }

        if (properties.getSecurity().isAllowDevHeaders()) {
            return headerActorProvider.current();
        }
        return new Actor(Actor.ANONYMOUS_ID, ActorRole.CLIENTE);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
