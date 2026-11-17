package pe.edu.upc.luzdelsur.incidencias.auth;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<AppUser> findByRoleOrderByFullNameAsc(ActorRole role);

    List<AppUser> findByIdIn(Collection<UUID> ids);
}
