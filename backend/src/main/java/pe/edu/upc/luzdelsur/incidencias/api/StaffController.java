package pe.edu.upc.luzdelsur.incidencias.api;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.upc.luzdelsur.incidencias.audit.AuditAction;
import pe.edu.upc.luzdelsur.incidencias.audit.AuditService;
import pe.edu.upc.luzdelsur.incidencias.auth.AppUser;
import pe.edu.upc.luzdelsur.incidencias.auth.AppUserRepository;
import pe.edu.upc.luzdelsur.incidencias.auth.AuthDtos;
import pe.edu.upc.luzdelsur.incidencias.auth.AuthService;
import pe.edu.upc.luzdelsur.incidencias.common.BadRequestException;
import pe.edu.upc.luzdelsur.incidencias.common.NotFoundException;
import pe.edu.upc.luzdelsur.incidencias.common.PageResponse;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentRepository;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.security.AccessControl;
import pe.edu.upc.luzdelsur.incidencias.security.Actor;
import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

/**
 * Gestion de tecnicos y consulta de auditoria por parte del operador.
 */
@RestController
@RequestMapping("/api/v1/operator")
@Tag(name = "Operador - tecnicos y auditoria", description = "Directorio de tecnicos, altas y eventos de auditoria")
public class StaffController {

    private static final int MAX_PAGE_SIZE = 100;

    private static final EnumSet<IncidentStatus> OPEN_ASSIGNMENT_STATUSES = EnumSet.of(
            IncidentStatus.CONFIRMADA, IncidentStatus.EN_ATENCION, IncidentStatus.REABIERTA);

    private final AppUserRepository users;
    private final AuthService authService;
    private final IncidentRepository incidents;
    private final AccessControl accessControl;
    private final AuditService audit;

    public StaffController(AppUserRepository users, AuthService authService, IncidentRepository incidents,
                           AccessControl accessControl, AuditService audit) {
        this.users = users;
        this.authService = authService;
        this.incidents = incidents;
        this.accessControl = accessControl;
        this.audit = audit;
    }

    @GetMapping("/technicians")
    @Operation(summary = "Tecnicos registrados con su carga de casos abiertos")
    @Transactional(readOnly = true)
    public List<AuthDtos.TechnicianView> technicians(
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        accessControl.require(ActorRole.OPERADOR);
        return users.findByRoleOrderByFullNameAsc(ActorRole.TECNICO).stream()
                .filter(user -> !onlyActive || user.isActive())
                .map(this::toView)
                .toList();
    }

    @PostMapping("/technicians")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una cuenta de tecnico")
    public AuthDtos.TechnicianView createTechnician(@Valid @RequestBody AuthDtos.StaffCreateRequest request) {
        Actor actor = accessControl.require(ActorRole.OPERADOR);
        AppUser created = authService.createUser(request.name(), request.username(), request.password(),
                ActorRole.TECNICO);
        audit.record(actor, AuditAction.USUARIO_CREADO, "USUARIO", created.getId(), "rol=TECNICO");
        return toView(created);
    }

    @PatchMapping("/technicians/{technicianId}")
    @Operation(summary = "Activa o desactiva a un tecnico (un tecnico desactivado no puede iniciar sesion)")
    @Transactional
    public AuthDtos.TechnicianView updateTechnician(@PathVariable UUID technicianId,
                                                    @RequestBody AuthDtos.StaffUpdateRequest request) {
        Actor actor = accessControl.require(ActorRole.OPERADOR);
        AppUser user = users.findById(technicianId)
                .filter(found -> found.getRole() == ActorRole.TECNICO)
                .orElseThrow(() -> new NotFoundException("Tecnico no encontrado: " + technicianId));
        if (request == null || request.active() == null) {
            throw new BadRequestException("Indique el campo 'active'");
        }
        user.setActive(request.active());
        users.save(user);
        audit.record(actor, AuditAction.USUARIO_ACTUALIZADO, "USUARIO", user.getId(), "activo=" + request.active());
        return toView(user);
    }

    @GetMapping("/audit-events")
    @Operation(summary = "Eventos de auditoria (accesos y acciones sensibles), del mas reciente al mas antiguo")
    public PageResponse<AuditService.AuditEventView> auditEvents(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        accessControl.require(ActorRole.OPERADOR);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return audit.search(actorId, action, entityId, from, to, pageable);
    }

    private AuthDtos.TechnicianView toView(AppUser user) {
        long open = incidents.countByAssignedTechnicianIdAndStatusIn(user.getId().toString(),
                OPEN_ASSIGNMENT_STATUSES);
        return new AuthDtos.TechnicianView(user.getId(), user.getFullName(), user.getUsername(),
                user.isActive(), open, user.getLastLoginAt());
    }
}
