package pe.edu.upc.luzdelsur.incidencias.auth;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.upc.luzdelsur.incidencias.audit.AuditAction;
import pe.edu.upc.luzdelsur.incidencias.audit.AuditService;
import pe.edu.upc.luzdelsur.incidencias.common.ConflictException;
import pe.edu.upc.luzdelsur.incidencias.common.ForbiddenException;
import pe.edu.upc.luzdelsur.incidencias.common.NotFoundException;
import pe.edu.upc.luzdelsur.incidencias.common.UnauthorizedException;
import pe.edu.upc.luzdelsur.incidencias.config.AppProperties;
import pe.edu.upc.luzdelsur.incidencias.security.Actor;
import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

/**
 * Registro, inicio de sesion, renovacion y consulta del usuario autenticado.
 *
 * <p>Los mensajes de error de login no distinguen entre usuario inexistente y
 * contrasena incorrecta, para no revelar que cuentas existen.</p>
 */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Usuario o contrasena incorrectos";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditService audit, AppProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        ActorRole role = request.role() == null ? ActorRole.CLIENTE : request.role();
        if (role == ActorRole.SISTEMA) {
            throw new ForbiddenException("El rol SISTEMA no se puede registrar");
        }
        if (role != ActorRole.CLIENTE && !properties.getAuth().isAllowStaffRegistration()) {
            throw new ForbiddenException(
                    "El registro publico solo crea cuentas de cliente. Las cuentas de operador y tecnico "
                            + "las crea un operador");
        }

        AppUser user = createUser(request.name(), request.username(), request.password(), role);
        user.markLogin(clock.instant());
        audit.record(user.getId().toString(), role.name(), AuditAction.REGISTRO, "USUARIO", user.getId(), null);
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String username = normalizeUsername(request.username());
        AppUser user = users.findByUsername(username).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            audit.record(null, null, AuditAction.LOGIN_FALLIDO, "USUARIO", null, "usuario=" + username);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            audit.record(user.getId().toString(), user.getRole().name(), AuditAction.LOGIN_FALLIDO,
                    "USUARIO", user.getId(), "cuenta desactivada");
            throw new ForbiddenException("La cuenta esta desactivada. Contacte a un operador");
        }
        if (request.role() != null && request.role() != user.getRole()) {
            audit.record(user.getId().toString(), user.getRole().name(), AuditAction.LOGIN_FALLIDO,
                    "USUARIO", user.getId(), "rol seleccionado " + request.role());
            throw new ForbiddenException("Esta cuenta no tiene el rol " + request.role()
                    + ". Seleccione el rol " + user.getRole() + " para ingresar");
        }

        user.markLogin(clock.instant());
        users.save(user);
        audit.record(user.getId().toString(), user.getRole().name(), AuditAction.LOGIN_OK,
                "USUARIO", user.getId(), null);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse refresh(String refreshToken) {
        String subject = jwtService.parseRefreshSubject(refreshToken);
        AppUser user = findActive(subject);
        audit.record(user.getId().toString(), user.getRole().name(), AuditAction.TOKEN_REFRESCADO,
                "USUARIO", user.getId(), null);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthUser me(Actor actor) {
        if (actor == null || actor.isAnonymous()) {
            throw new UnauthorizedException("Debe iniciar sesion");
        }
        return AuthDtos.AuthUser.from(findActive(actor.id()));
    }

    /**
     * Crea una cuenta validando unicidad del usuario. Lo usan el registro,
     * la gestion de tecnicos y la semilla de demo.
     */
    @Transactional
    public AppUser createUser(String name, String username, String rawPassword, ActorRole role) {
        String normalized = normalizeUsername(username);
        if (users.existsByUsername(normalized)) {
            throw new ConflictException("Ya existe una cuenta con el usuario " + normalized);
        }
        AppUser user = AppUser.create(normalized, name.trim(), passwordEncoder.encode(rawPassword),
                role, clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Ya existe una cuenta con el usuario " + normalized);
        }
    }

    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    private AppUser findActive(String subject) {
        UUID id;
        try {
            id = UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("La sesion no corresponde a una cuenta registrada");
        }
        AppUser user = users.findById(id)
                .orElseThrow(() -> new NotFoundException("La cuenta de la sesion ya no existe"));
        if (!user.isActive()) {
            throw new ForbiddenException("La cuenta esta desactivada. Contacte a un operador");
        }
        return user;
    }

    private AuthDtos.AuthResponse issueTokens(AppUser user) {
        JwtService.IssuedToken access = jwtService.issueAccess(user);
        JwtService.IssuedToken refresh = jwtService.issueRefresh(user);
        return new AuthDtos.AuthResponse(
                access.token(),
                "Bearer",
                jwtService.accessTtlSeconds(),
                access.expiresAt(),
                refresh.token(),
                refresh.expiresAt(),
                AuthDtos.AuthUser.from(user));
    }
}
