package pe.edu.upc.luzdelsur.incidencias.report;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.upc.luzdelsur.incidencias.api.ApiMapper;
import pe.edu.upc.luzdelsur.incidencias.api.dto.ReportDtos;
import pe.edu.upc.luzdelsur.incidencias.common.BadRequestException;
import pe.edu.upc.luzdelsur.incidencias.common.ConflictException;
import pe.edu.upc.luzdelsur.incidencias.common.NotFoundException;
import pe.edu.upc.luzdelsur.incidencias.common.TextNormalizer;
import pe.edu.upc.luzdelsur.incidencias.grouping.GroupingProposal;
import pe.edu.upc.luzdelsur.incidencias.grouping.GroupingService;
import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.incident.Incident;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentMetrics;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentRepository;
import pe.edu.upc.luzdelsur.incidencias.security.Actor;
import pe.edu.upc.luzdelsur.incidencias.security.AccessControl;

/**
 * Registro de reportes y consulta por parte del cliente.
 *
 * <p>Flujo (punto 4.4 del informe): el reporte se persiste siempre; luego el
 * motor propone una agrupacion y el reporte queda vinculado como SUGERIDO a la
 * espera de la confirmacion humana. Nunca se descarta informacion original.</p>
 */
@Service
public class ReportService {

    private final ReportRepository reports;
    private final IncidentRepository incidents;
    private final ReportLinkHistoryRepository history;
    private final GroupingService grouping;
    private final IncidentMetrics metrics;
    private final AccessControl accessControl;
    private final ApiMapper mapper;
    private final java.time.Clock clock;

    public ReportService(ReportRepository reports,
                         IncidentRepository incidents,
                         ReportLinkHistoryRepository history,
                         GroupingService grouping,
                         IncidentMetrics metrics,
                         AccessControl accessControl,
                         ApiMapper mapper,
                         java.time.Clock clock) {
        this.reports = reports;
        this.incidents = incidents;
        this.history = history;
        this.grouping = grouping;
        this.metrics = metrics;
        this.accessControl = accessControl;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public ReportDtos.CreateResponse create(ReportDtos.CreateRequest request) {
        Instant now = clock.instant();
        Instant reportedAt = request.reportedAt() == null ? now : request.reportedAt();
        if (reportedAt.isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw new BadRequestException("reportedAt no puede estar en el futuro");
        }

        String sector = trimToNull(request.sector());
        boolean ambiguousSector = TextNormalizer.isAmbiguousSector(sector);
        String sectorKey = ambiguousSector ? null : TextNormalizer.normalizeKey(sector);
        String description = request.description().trim();
        Actor actor = accessControl.currentActor();

        GroupingProposal proposal = grouping.propose(
                sectorKey, request.failureType(), description, reportedAt);

        Incident incident = resolveIncident(proposal, sector, sectorKey, request.failureType(), reportedAt, now);
        incidents.save(incident);

        Report report = Report.register(
                UUID.randomUUID(),
                trimToNull(request.clientId()),
                sector,
                sectorKey,
                trimToNull(request.reference()),
                request.failureType(),
                description,
                toDecimal(request.latitude()),
                toDecimal(request.longitude()),
                reportedAt,
                now);

        report.linkTo(incident,
                proposal.certainty(),
                truncate(proposal.reasonsAsText(), 1000),
                proposal.similarity(),
                now,
                actor.id());
        reports.save(report);

        history.save(ReportLinkHistory.record(
                report.getId(), null, incident.getId(), LinkAction.VINCULADO,
                actor.id(), truncate(proposal.reasonsAsText(), 500), now));

        metrics.refresh(incident);
        incidents.save(incident);

        return mapper.createResponse(report, incident, proposal);
    }

    @Transactional(readOnly = true)
    public ReportDtos.TrackingResponse tracking(UUID reportId) {
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Reporte no encontrado: " + reportId));
        Incident incident = report.getIncident();
        return mapper.tracking(report, incident, mapper.clientMessageForStatus(incident));
    }

    private Incident resolveIncident(GroupingProposal proposal, String sector, String sectorKey,
                                     FailureType failureType, Instant reportedAt, Instant now) {
        if (proposal.candidateIncidentId() != null) {
            Incident candidate = incidents.findById(proposal.candidateIncidentId())
                    .orElseThrow(() -> new ConflictException(
                            "El caso candidato ya no esta disponible. Vuelva a registrar el reporte"));
            if (!candidate.getStatus().canReceiveReports()) {
                throw new ConflictException(
                        "El caso " + candidate.getId() + " ya no admite reportes (estado "
                                + candidate.getStatus() + "). Vuelva a registrar el reporte");
            }
            candidate.applySectorIfMissing(displaySector(sector), keyOrUnknown(sectorKey));
            return candidate;
        }

        return Incident.start(
                UUID.randomUUID(),
                displaySector(sector),
                keyOrUnknown(sectorKey),
                failureType,
                reportedAt,
                proposal.certainty(),
                now);
    }

    private String displaySector(String sector) {
        return sector == null ? Incident.UNKNOWN_SECTOR_LABEL : sector;
    }

    private String keyOrUnknown(String sectorKey) {
        return sectorKey == null ? Incident.UNKNOWN_SECTOR_KEY : sectorKey;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal toDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
