package pe.edu.upc.luzdelsur.incidencias.api;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.upc.luzdelsur.incidencias.api.dto.OperatorDtos;
import pe.edu.upc.luzdelsur.incidencias.api.dto.TechnicianDtos;
import pe.edu.upc.luzdelsur.incidencias.common.ForbiddenException;
import pe.edu.upc.luzdelsur.incidencias.common.PageResponse;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentService;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.security.AccessControl;
import pe.edu.upc.luzdelsur.incidencias.security.Actor;
import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

@RestController
@RequestMapping("/api/v1/technician")
@Tag(name = "Tecnico de campo", description = "Bandeja y contexto consolidado de casos confirmados")
public class TechnicianController {

    private static final int MAX_PAGE_SIZE = 50;

    private static final Set<IncidentStatus> ALLOWED_TARGETS = EnumSet.of(
            IncidentStatus.EN_ATENCION, IncidentStatus.RESUELTA);

    private final IncidentService incidentService;
    private final AccessControl accessControl;

    public TechnicianController(IncidentService incidentService, AccessControl accessControl) {
        this.incidentService = incidentService;
        this.accessControl = accessControl;
    }

    @GetMapping("/incidents")
    @Operation(summary = "Bandeja del tecnico: casos confirmados, en atencion o reabiertos")
    public PageResponse<TechnicianDtos.TechnicianIncidentSummary> incidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) String sector,
            @RequestParam(defaultValue = "false") boolean onlyMine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Actor actor = accessControl.require(ActorRole.TECNICO);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("priorityScore"), Sort.Order.asc("firstReportedAt")));

        return incidentService.searchTechnicianIncidents(
                status, sector, onlyMine, actor.id(), pageable);
    }

    @GetMapping("/incidents/{incidentId}")
    @Operation(summary = "Ubicacion, alcance estimado y reportes vinculados del caso confirmado")
    public TechnicianDtos.TechnicianIncidentView view(@PathVariable UUID incidentId) {
        accessControl.require(ActorRole.TECNICO);
        return incidentService.technicianView(incidentId);
    }

    @PostMapping("/incidents/{incidentId}/status")
    @Operation(summary = "El tecnico inicia la atencion o marca el servicio como restablecido")
    public OperatorDtos.IncidentDetail changeStatus(@PathVariable UUID incidentId,
                                                    @Valid @RequestBody OperatorDtos.StatusRequest request) {
        Actor actor = accessControl.require(ActorRole.TECNICO);
        if (!ALLOWED_TARGETS.contains(request.status())) {
            throw new ForbiddenException("El tecnico solo puede cambiar el caso a EN_ATENCION o RESUELTA");
        }
        return incidentService.changeStatus(incidentId, request.status(), request.note(), actor);
    }
}
