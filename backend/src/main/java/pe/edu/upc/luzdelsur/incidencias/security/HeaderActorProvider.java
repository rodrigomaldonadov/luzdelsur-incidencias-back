package pe.edu.upc.luzdelsur.incidencias.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import pe.edu.upc.luzdelsur.incidencias.common.BadRequestException;
import pe.edu.upc.luzdelsur.incidencias.config.AppProperties;

import java.util.Locale;

/**
 * Implementacion temporal para el prototipo.
 *
 * <p>Lee la identidad de las cabeceras {@code X-Actor-Id} y {@code X-Actor-Role}.
 * No valida firmas: la autenticacion real (JWT + roles) corresponde a Persona 3.
 * Se mantiene aislada detras de {@link ActorProvider} para no reescribir la API.</p>
 */
@Component
public class HeaderActorProvider implements ActorProvider {

    private static final String ACTOR_ID_HEADER = "X-Actor-Id";
    private static final String ACTOR_ROLE_HEADER = "X-Actor-Role";

    private final AppProperties properties;

    public HeaderActorProvider(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public Actor current() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return new Actor(Actor.ANONYMOUS_ID, defaultRole());
        }

        String id = trimToNull(request.getHeader(ACTOR_ID_HEADER));
        String roleHeader = trimToNull(request.getHeader(ACTOR_ROLE_HEADER));

        ActorRole role = roleHeader == null ? defaultRole() : parseRole(roleHeader);
        return new Actor(id == null ? Actor.ANONYMOUS_ID : id, role);
    }

    private ActorRole defaultRole() {
        return parseRole(properties.getSecurity().getDevDefaultRole());
    }

    private ActorRole parseRole(String value) {
        try {
            return ActorRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "Rol no valido: '" + value + "'. Valores permitidos: CLIENTE, OPERADOR, TECNICO, SISTEMA");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
