package pe.edu.upc.luzdelsur.incidencias.api;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.upc.luzdelsur.incidencias.api.dto.ReportDtos;
import pe.edu.upc.luzdelsur.incidencias.audit.AuditAction;
import pe.edu.upc.luzdelsur.incidencias.audit.AuditService;
import pe.edu.upc.luzdelsur.incidencias.common.PageResponse;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentService;
import pe.edu.upc.luzdelsur.incidencias.report.ReportService;
import pe.edu.upc.luzdelsur.incidencias.security.AccessControl;
import pe.edu.upc.luzdelsur.incidencias.security.Actor;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reportes del cliente", description = "Registro y seguimiento del reporte original")
public class ReportController {

    private final ReportService reportService;
    private final IncidentService incidentService;
    private final AccessControl accessControl;
    private final AuditService audit;

    public ReportController(ReportService reportService, IncidentService incidentService,
                            AccessControl accessControl, AuditService audit) {
        this.reportService = reportService;
        this.incidentService = incidentService;
        this.accessControl = accessControl;
        this.audit = audit;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un reporte y ejecuta el motor de agrupacion")
    public ReportDtos.CreateResponse create(@Valid @RequestBody ReportDtos.CreateRequest request) {
        ReportDtos.CreateResponse response = reportService.create(request);
        audit.record(accessControl.currentActor(), AuditAction.REPORTE_CREADO, "REPORTE",
                response.report().id(), "incidencia=" + response.incidentId() + " certeza=" + response.certainty());
        return response;
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Consulta el estado comprensible del reporte para el cliente")
    public ReportDtos.TrackingResponse tracking(@PathVariable UUID reportId) {
        return reportService.tracking(reportId);
    }

    @GetMapping("/mine")
    @Operation(summary = "Lista los reportes del cliente autenticado (JWT)")
    public PageResponse<ReportDtos.TrackingResponse> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("reportedAt")));
        return reportService.myReports(pageable);
    }

    @PostMapping("/{reportId}/persistence")
    @Operation(summary = "Caso C7: el cliente indica que la falla persiste y el caso se reabre")
    public ReportDtos.TrackingResponse reportPersistence(
            @PathVariable UUID reportId,
            @Valid @RequestBody(required = false) ReportDtos.PersistenceRequest request) {
        String note = request == null ? null : request.note();
        Actor actor = accessControl.currentActor();
        ReportDtos.TrackingResponse response = incidentService.markPersistence(reportId, note, actor);
        audit.record(actor, AuditAction.PERSISTENCIA_REPORTADA, "REPORTE", reportId,
                "estado=" + response.incidentStatus());
        return response;
    }

    @PostMapping("/{reportId}/feedback")
    @Operation(summary = "El cliente califica la atencion (1 a 5) cuando el caso esta resuelto o cerrado")
    public ReportDtos.TrackingResponse rate(@PathVariable UUID reportId,
                                            @Valid @RequestBody ReportDtos.FeedbackRequest request) {
        ReportDtos.TrackingResponse response = reportService.rate(reportId, request.rating(), request.comment());
        audit.record(accessControl.currentActor(), AuditAction.CALIFICACION_REGISTRADA, "REPORTE", reportId,
                "calificacion=" + request.rating());
        return response;
    }
}
