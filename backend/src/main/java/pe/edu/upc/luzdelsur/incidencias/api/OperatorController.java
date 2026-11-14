package pe.edu.upc.luzdelsur.incidencias.api;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.upc.luzdelsur.incidencias.api.dto.OperatorDtos;
import pe.edu.upc.luzdelsur.incidencias.api.dto.ReportDtos;
import pe.edu.upc.luzdelsur.incidencias.common.PageResponse;
import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentService;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;
import pe.edu.upc.luzdelsur.incidencias.report.ReportLinkStatus;
import pe.edu.upc.luzdelsur.incidencias.security.AccessControl;
import pe.edu.upc.luzdelsur.incidencias.security.Actor;
import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

@RestController
@RequestMapping("/api/v1/operator")
@Tag(name = "Operador", description = "Bandeja priorizada, confirmacion, separacion y reasignacion")
public class OperatorController {

    private static final int MAX_PAGE_SIZE = 50;

    private final IncidentService incidentService;
    private final AccessControl accessControl;

    public OperatorController(IncidentService incidentService, AccessControl accessControl) {
        this.incidentService = incidentService;
        this.accessControl = accessControl;
    }

    @GetMapping("/incidents")
    @Operation(summary = "Bandeja priorizada de incidencias")
    public PageResponse<OperatorDtos.IncidentSummary> incidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Certainty certainty,
            @RequestParam(required = false) Boolean requiresReview,
            @RequestParam(required = false) FailureType failureType,
            @RequestParam(required = false) String sector,
            @RequestParam(defaultValue = "true") boolean onlyOpen,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        accessControl.require(ActorRole.OPERADOR);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("priorityScore"), Sort.Order.asc("firstReportedAt")));

        return incidentService.searchIncidents(status, certainty, requiresReview,
                failureType, sector, onlyOpen, pageable);
    }

    @GetMapping("/incidents/{incidentId}")
    @Operation(summary = "Detalle del caso con reportes de origen e historial de vinculos")
    public OperatorDtos.IncidentDetail detail(@PathVariable UUID incidentId) {
        accessControl.require(ActorRole.OPERADOR);
        return incidentService.detail(incidentId);
    }

    @PostMapping("/incidents/{incidentId}/confirm")
    @Operation(summary = "Confirma la agrupacion sugerida y pasa el caso a CONFIRMADA")
    public OperatorDtos.IncidentDetail confirm(@PathVariable UUID incidentId,
                                               @Valid @RequestBody(required = false) OperatorDtos.ConfirmRequest request) {
        Actor actor = accessControl.require(ActorRole.OPERADOR);
        String note = request == null ? null : request.note();
        return incidentService.confirm(incidentId, note, actor);
    }

    @PostMapping("/incidents/{incidentId}/status")
    @Operation(summary = "Registra un cambio de estado validando la maquina de estados")
    public OperatorDtos.IncidentDetail changeStatus(@PathVariable UUID incidentId,
                                                    @Valid @RequestBody OperatorDtos.StatusRequest request) {
        Actor actor = accessControl.require(ActorRole.OPERADOR);
        return incidentService.changeStatus(incidentId, request.status(), request.note(), actor);
    }

    @PostMapping("/reports/{reportId}/separate")
    @Operation(summary = "Separa un reporte del caso; conserva su UUID e historial")
    public OperatorDtos.SeparationResult separate(@PathVariable UUID reportId,
                                                  @Valid @RequestBody(required = false) OperatorDtos.SeparateRequest request) {
        Actor actor = accessControl.require(ActorRole.OPERADOR);
        Boolean createNew = request == null ? Boolean.TRUE : request.createNewIncident();
        String note = request == null ? null : request.note();
        return incidentService.separate(reportId, createNew, note, actor);
    }

    @PostMapping("/reports/{reportId}/reassign")
    @Operation(summary = "Reasigna un reporte a otra incidencia abierta")
    public OperatorDtos.IncidentDetail reassign(@PathVariable UUID reportId,
                                                @Valid @RequestBody OperatorDtos.ReassignRequest request) {
        Actor actor = accessControl.require(ActorRole.OPERADOR);
        return incidentService.reassign(reportId, request.targetIncidentId(), request.note(), actor);
    }

    @GetMapping("/reports")
    @Operation(summary = "Bandeja base de reportes individuales (alternativa A para comparar H4)")
    public PageResponse<ReportDtos.ReportView> reports(
            @RequestParam(required = false) UUID incidentId,
            @RequestParam(required = false) ReportLinkStatus linkStatus,
            @RequestParam(required = false) FailureType failureType,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        accessControl.require(ActorRole.OPERADOR);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("reportedAt")));

        return incidentService.searchReports(incidentId, linkStatus, failureType,
                sector, from, to, pageable);
    }
}
