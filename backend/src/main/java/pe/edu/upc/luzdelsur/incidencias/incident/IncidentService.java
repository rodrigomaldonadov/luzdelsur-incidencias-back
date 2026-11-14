package pe.edu.upc.luzdelsur.incidencias.incident;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.upc.luzdelsur.incidencias.api.ApiMapper;
import pe.edu.upc.luzdelsur.incidencias.api.dto.OperatorDtos;
import pe.edu.upc.luzdelsur.incidencias.api.dto.ReportDtos;
import pe.edu.upc.luzdelsur.incidencias.api.dto.TechnicianDtos;
import pe.edu.upc.luzdelsur.incidencias.common.ConflictException;
import pe.edu.upc.luzdelsur.incidencias.common.NotFoundException;
import pe.edu.upc.luzdelsur.incidencias.common.PageResponse;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;
import pe.edu.upc.luzdelsur.incidencias.report.LinkAction;
import pe.edu.upc.luzdelsur.incidencias.report.Report;
import pe.edu.upc.luzdelsur.incidencias.report.ReportLinkHistory;
import pe.edu.upc.luzdelsur.incidencias.report.ReportLinkHistoryRepository;
import pe.edu.upc.luzdelsur.incidencias.report.ReportLinkStatus;
import pe.edu.upc.luzdelsur.incidencias.report.ReportRepository;
import pe.edu.upc.luzdelsur.incidencias.report.ReportSpecifications;
import pe.edu.upc.luzdelsur.incidencias.security.Actor;

/**
 * Ciclo de vida de la incidencia: confirmacion, estados, separacion,
 * reasignacion y reapertura. Toda correccion conserva el reporte original y
 * deja un registro en {@code report_link_history}.
 */
@Service
public class IncidentService {

    private static final int MAX_NOTE_LENGTH = 500;

    private final IncidentRepository incidents;
    private final ReportRepository reports;
    private final ReportLinkHistoryRepository history;
    private final IncidentMetrics metrics;
    private final ApiMapper mapper;
    private final java.time.Clock clock;

    public IncidentService(IncidentRepository incidents,
                           ReportRepository reports,
                           ReportLinkHistoryRepository history,
                           IncidentMetrics metrics,
                           ApiMapper mapper,
                           java.time.Clock clock) {
        this.incidents = incidents;
        this.reports = reports;
        this.history = history;
        this.metrics = metrics;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OperatorDtos.IncidentSummary> searchIncidents(IncidentStatus status,
                                                                     Certainty certainty,
                                                                     Boolean requiresReview,
                                                                     FailureType failureType,
                                                                     String sector,
                                                                     boolean onlyOpen,
                                                                     Pageable pageable) {
        Specification<Incident> spec = (root, query, cb) -> cb.conjunction();
        spec = and(spec, IncidentSpecifications.hasStatus(status));
        spec = and(spec, IncidentSpecifications.hasCertainty(certainty));
        spec = and(spec, IncidentSpecifications.requiresReview(requiresReview));
        spec = and(spec, IncidentSpecifications.hasFailureType(failureType));
        spec = and(spec, IncidentSpecifications.sectorContains(sector));
        if (onlyOpen) {
            spec = and(spec, IncidentSpecifications.isOpen());
        }

        Page<Incident> page = incidents.findAll(spec, pageable);
        return PageResponse.from(page, mapper::summary);
    }

    @Transactional(readOnly = true)
    public OperatorDtos.IncidentDetail detail(UUID incidentId) {
        return buildDetail(requireIncident(incidentId));
    }

    @Transactional
    public OperatorDtos.IncidentDetail confirm(UUID incidentId, String note, Actor actor) {
        Incident incident = requireIncident(incidentId);
        if (incident.getStatus() != IncidentStatus.SUGERIDA) {
            throw new ConflictException("Solo se puede confirmar un caso en estado SUGERIDA. Estado actual: "
                    + incident.getStatus());
        }

        Instant now = clock.instant();
        incident.markConfirmed(actor.id(), now);

        List<Report> linked = reports.findByIncidentIdOrderByReportedAtAsc(incidentId);
        for (Report report : linked) {
            report.confirmLink(now, actor.id());
            history.save(ReportLinkHistory.record(report.getId(), incidentId, incidentId,
                    LinkAction.CONFIRMADO, actor.id(), truncate(note), now));
        }
        reports.saveAll(linked);

        metrics.refresh(incident);
        incidents.save(incident);
        return buildDetail(incident);
    }

    @Transactional
    public OperatorDtos.IncidentDetail changeStatus(UUID incidentId, IncidentStatus target,
                                                    String note, Actor actor) {
        if (target == IncidentStatus.CONFIRMADA) {
            return confirm(incidentId, note, actor);
        }

        Incident incident = requireIncident(incidentId);
        if (!incident.getStatus().canTransitionTo(target)) {
            throw new ConflictException("Transicion no permitida: " + incident.getStatus() + " -> " + target);
        }

        Instant now = clock.instant();

        if (target == IncidentStatus.DESCARTADA) {
            List<Report> linked = reports.findByIncidentIdOrderByReportedAtAsc(incidentId);
            for (Report report : linked) {
                report.detach(now, actor.id());
                history.save(ReportLinkHistory.record(report.getId(), incidentId, null,
                        LinkAction.SEPARADO, actor.id(),
                        truncate("Caso descartado. " + (note == null ? "" : note)), now));
            }
            reports.saveAll(linked);
        }

        incident.changeStatus(target, actor.id(), now);
        metrics.refresh(incident);
        incidents.save(incident);
        return buildDetail(incident);
    }

    /**
     * Separa un reporte de su incidencia. Por defecto crea un caso nuevo para
     * que el reporte no quede huerfano, conservando siempre el UUID original.
     */
    @Transactional
    public OperatorDtos.SeparationResult separate(UUID reportId, Boolean createNewIncident,
                                                  String note, Actor actor) {
        Report report = requireReport(reportId);
        Incident source = report.getIncident();
        if (source == null) {
            throw new ConflictException("El reporte no esta vinculado a una incidencia");
        }
        if (!source.getStatus().canReceiveReports()) {
            throw new ConflictException("El caso ya no admite cambios de agrupacion (estado "
                    + source.getStatus() + ")");
        }

        Instant now = clock.instant();
        UUID sourceId = source.getId();

        report.detach(now, actor.id());
        history.save(ReportLinkHistory.record(reportId, sourceId, null, LinkAction.SEPARADO,
                actor.id(), truncate(note), now));
        reports.save(report);

        metrics.refresh(source);

        UUID newIncidentId = null;
        boolean shouldCreate = createNewIncident == null || createNewIncident;
        if (shouldCreate) {
            Incident created = Incident.start(
                    UUID.randomUUID(),
                    report.getSector() == null ? Incident.UNKNOWN_SECTOR_LABEL : report.getSector(),
                    report.getSectorKey() == null ? Incident.UNKNOWN_SECTOR_KEY : report.getSectorKey(),
                    report.getFailureType(),
                    report.getReportedAt(),
                    Certainty.ALTA,
                    now);
            incidents.save(created);

            report.linkTo(created, Certainty.ALTA,
                    truncate("Separado por el operador y registrado como caso independiente. " + safe(note), 1000),
                    null, now, actor.id());
            reports.save(report);
            history.save(ReportLinkHistory.record(reportId, null, created.getId(), LinkAction.VINCULADO,
                    actor.id(), truncate("Caso nuevo por separacion. " + safe(note)), now));

            metrics.refresh(created);
            incidents.save(created);

            newIncidentId = created.getId();
        }

        if (source.getReportCount() == 0) {
            source.changeStatus(IncidentStatus.DESCARTADA, actor.id(), now);
            incidents.save(source);
        }

        return new OperatorDtos.SeparationResult(buildDetail(source), newIncidentId);
    }

    @Transactional
    public OperatorDtos.IncidentDetail reassign(UUID reportId, UUID targetIncidentId,
                                                String note, Actor actor) {
        Report report = requireReport(reportId);
        Incident target = requireIncident(targetIncidentId);

        if (!target.getStatus().canReceiveReports()) {
            throw new ConflictException("El caso destino no admite reportes (estado " + target.getStatus() + ")");
        }

        Incident source = report.getIncident();
        if (source != null && source.getId().equals(targetIncidentId)) {
            throw new ConflictException("El reporte ya pertenece a la incidencia indicada");
        }
        if (source != null && !source.getStatus().canReceiveReports()) {
            throw new ConflictException("El caso de origen ya no admite cambios (estado " + source.getStatus() + ")");
        }

        Instant now = clock.instant();
        UUID sourceId = source == null ? null : source.getId();

        boolean sameSector = report.getSectorKey() != null
                && report.getSectorKey().equals(target.getSectorKey());
        boolean sameType = report.getFailureType() == target.getFailureType();
        Certainty certainty = sameSector && sameType ? Certainty.MEDIA : Certainty.BAJA;

        report.moveTo(target, certainty,
                truncate("Reasignado manualmente por el operador. " + safe(note), 1000),
                now, actor.id());
        reports.save(report);

        history.save(ReportLinkHistory.record(reportId, sourceId, targetIncidentId,
                LinkAction.REASIGNADO, actor.id(), truncate(note), now));

        target.applySectorIfMissing(
                report.getSector() == null ? Incident.UNKNOWN_SECTOR_LABEL : report.getSector(),
                report.getSectorKey() == null ? Incident.UNKNOWN_SECTOR_KEY : report.getSectorKey());

        if (source != null) {
            metrics.refresh(source);
            if (source.getReportCount() == 0) {
                source.changeStatus(IncidentStatus.DESCARTADA, actor.id(), now);
            }
            incidents.save(source);
        }

        metrics.refresh(target);
        incidents.save(target);
        return buildDetail(target);
    }

    /**
     * Caso C7: el cliente indica que la falla persiste sobre un caso resuelto.
     * Se reabre sin perder el historial.
     */
    @Transactional
    public ReportDtos.TrackingResponse markPersistence(UUID reportId, String note, Actor actor) {
        Report report = requireReport(reportId);
        Incident incident = report.getIncident();
        if (incident == null) {
            throw new ConflictException("El reporte no esta vinculado a una incidencia");
        }

        Instant now = clock.instant();
        if (incident.getStatus() == IncidentStatus.RESUELTA || incident.getStatus() == IncidentStatus.CERRADA) {
            incident.changeStatus(IncidentStatus.REABIERTA, actor.id(), now);
            metrics.refresh(incident);
            incidents.save(incident);
            history.save(ReportLinkHistory.record(reportId, incident.getId(), incident.getId(),
                    LinkAction.REABIERTO, actor.id(), truncate(note), now));
        } else if (incident.getStatus() == IncidentStatus.DESCARTADA) {
            throw new ConflictException("El caso fue descartado. Registre un nuevo reporte");
        }

        return mapper.tracking(report, incident, mapper.clientMessageForStatus(incident));
    }

    @Transactional(readOnly = true)
    public TechnicianDtos.TechnicianIncidentView technicianView(UUID incidentId) {
        Incident incident = requireIncident(incidentId);
        if (incident.getStatus() == IncidentStatus.SUGERIDA
                || incident.getStatus() == IncidentStatus.DESCARTADA) {
            throw new ConflictException("El caso aun no fue confirmado por el operador (estado "
                    + incident.getStatus() + ")");
        }
        List<Report> linked = reports.findByIncidentIdOrderByReportedAtAsc(incidentId);
        return mapper.technicianView(incident, linked);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportDtos.ReportView> searchReports(UUID incidentId,
                                                             ReportLinkStatus linkStatus,
                                                             FailureType failureType,
                                                             String sector,
                                                             Instant from,
                                                             Instant to,
                                                             Pageable pageable) {
        Specification<Report> spec = (root, query, cb) -> cb.conjunction();
        spec = and(spec, ReportSpecifications.hasIncident(incidentId));
        spec = and(spec, ReportSpecifications.hasLinkStatus(linkStatus));
        spec = and(spec, ReportSpecifications.hasFailureType(failureType));
        spec = and(spec, ReportSpecifications.sectorContains(sector));
        spec = and(spec, ReportSpecifications.reportedFrom(from));
        spec = and(spec, ReportSpecifications.reportedTo(to));

        Page<Report> page = reports.findAll(spec, pageable);
        return PageResponse.from(page, mapper::reportView);
    }

    private OperatorDtos.IncidentDetail buildDetail(Incident incident) {
        List<Report> linked = reports.findByIncidentIdOrderByReportedAtAsc(incident.getId());
        List<ReportLinkHistory> histories = history.findByIncidentId(incident.getId());
        return mapper.detail(incident, linked, histories);
    }

    private Incident requireIncident(UUID incidentId) {
        return incidents.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("Incidencia no encontrada: " + incidentId));
    }

    private Report requireReport(UUID reportId) {
        return reports.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Reporte no encontrado: " + reportId));
    }

    private <T> Specification<T> and(Specification<T> base, Specification<T> extra) {
        return extra == null ? base : base.and(extra);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value) {
        return truncate(value, MAX_NOTE_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
