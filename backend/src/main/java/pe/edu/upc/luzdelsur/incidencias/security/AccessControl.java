package pe.edu.upc.luzdelsur.incidencias.security;

import org.springframework.stereotype.Component;

import pe.edu.upc.luzdelsur.incidencias.common.ForbiddenException;

@Component
public class AccessControl {

    private final ActorProvider actorProvider;

    public AccessControl(ActorProvider actorProvider) {
        this.actorProvider = actorProvider;
    }

    public Actor require(ActorRole... allowedRoles) {
        Actor actor = actorProvider.current();
        if (!actor.hasAnyRole(allowedRoles)) {
            throw new ForbiddenException(
                    "El rol " + actor.role() + " no tiene permiso para esta operacion. "
                            + "Roles permitidos: " + java.util.Arrays.toString(allowedRoles));
        }
        return actor;
    }

    public Actor currentActor() {
        return actorProvider.current();
    }
}
