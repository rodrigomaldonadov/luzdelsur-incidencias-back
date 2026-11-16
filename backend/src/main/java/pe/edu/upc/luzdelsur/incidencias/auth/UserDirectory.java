package pe.edu.upc.luzdelsur.incidencias.auth;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traduce identificadores de actor (UUID del JWT) a nombres legibles para el
 * historial. Los identificadores que no son cuentas (cabeceras de prueba,
 * "anonimo") se devuelven sin nombre.
 */
@Component
public class UserDirectory {

    private final AppUserRepository users;

    public UserDirectory(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Map<String, String> namesFor(Collection<String> actorIds) {
        Set<UUID> ids = new HashSet<>();
        for (String actorId : actorIds) {
            parse(actorId).ifPresent(ids::add);
        }
        Map<String, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        users.findByIdIn(ids).forEach(user -> names.put(user.getId().toString(), user.getFullName()));
        return names;
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> find(String actorId) {
        return parse(actorId).flatMap(users::findById);
    }

    static Optional<UUID> parse(String value) {
        if (value == null || value.length() != 36) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static boolean isAccountId(String value) {
        return parse(Objects.requireNonNullElse(value, "")).isPresent();
    }
}
