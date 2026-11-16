package pe.edu.upc.luzdelsur.incidencias.incident;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.upc.luzdelsur.incidencias.api.dto.OperatorDtos;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;
import pe.edu.upc.luzdelsur.incidencias.report.ReportRepository;

/**
 * Indicadores del panel del operador. El calculo se hace en memoria sobre las
 * incidencias: suficiente para el volumen del prototipo. Con volumen real se
 * reemplaza por consultas agregadas o una vista materializada.
 */
@Service
public class MonitoringService {

    private static final int TOP_SECTORS = 5;

    private final IncidentRepository incidents;
    private final ReportRepository reports;
    private final IncidentStatusEventRepository statusEvents;
    private final Clock clock;

    public MonitoringService(IncidentRepository incidents, ReportRepository reports,
                             IncidentStatusEventRepository statusEvents, Clock clock) {
        this.incidents = incidents;
        this.reports = reports;
        this.statusEvents = statusEvents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OperatorDtos.MetricsView metrics() {
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofHours(24));
        List<Incident> all = incidents.findAll();

        Map<IncidentStatus, Long> byStatus = new EnumMap<>(IncidentStatus.class);
        Map<PriorityLabel, Long> openByPriority = new EnumMap<>(PriorityLabel.class);
        Map<FailureType, Long> openByType = new EnumMap<>(FailureType.class);
        Map<String, long[]> sectors = new HashMap<>();
        Map<UUID, Incident> byId = new HashMap<>();

        long open = 0;
        long pendingReview = 0;
        long lowCertaintyOpen = 0;
        long unassignedConfirmed = 0;
        long incidentsLast24h = 0;

        for (Incident incident : all) {
            byId.put(incident.getId(), incident);
            byStatus.merge(incident.getStatus(), 1L, Long::sum);
            if (incident.getFirstReportedAt() != null && incident.getFirstReportedAt().isAfter(since)) {
                incidentsLast24h++;
            }
            if (!incident.getStatus().isOpen()) {
                continue;
            }
            open++;
            openByPriority.merge(incident.getPriorityLabel(), 1L, Long::sum);
            openByType.merge(incident.getFailureType(), 1L, Long::sum);
            if (incident.getStatus() == IncidentStatus.SUGERIDA) {
                pendingReview++;
            }
            if (incident.getCertainty() == Certainty.BAJA) {
                lowCertaintyOpen++;
            }
            if (incident.getStatus() == IncidentStatus.CONFIRMADA && incident.getAssignedTechnicianId() == null) {
                unassignedConfirmed++;
            }
            long[] counters = sectors.computeIfAbsent(incident.getSector(), key -> new long[2]);
            counters[0]++;
            counters[1] += incident.getReportCount();
        }

        OptionalDouble toConfirm = all.stream()
                .filter(incident -> incident.getConfirmedAt() != null && incident.getFirstReportedAt() != null)
                .mapToLong(incident -> Duration.between(incident.getFirstReportedAt(),
                        incident.getConfirmedAt()).toSeconds())
                .average();

        OptionalDouble toResolve = statusEvents.findAll().stream()
                .filter(event -> event.getToStatus() == IncidentStatus.RESUELTA
                        && event.getEventType() == IncidentStatusEvent.EventType.CAMBIO_ESTADO)
                .filter(event -> byId.containsKey(event.getIncidentId()))
                .mapToLong(event -> Duration.between(byId.get(event.getIncidentId()).getFirstReportedAt(),
                        event.getCreatedAt()).toSeconds())
                .average();

        List<OperatorDtos.SectorCount> topSectors = sectors.entrySet().stream()
                .map(entry -> new OperatorDtos.SectorCount(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingLong(OperatorDtos.SectorCount::openIncidents).reversed()
                        .thenComparing(Comparator.comparingLong(OperatorDtos.SectorCount::reports).reversed()))
                .limit(TOP_SECTORS)
                .toList();

        return new OperatorDtos.MetricsView(
                now,
                open,
                pendingReview,
                lowCertaintyOpen,
                unassignedConfirmed,
                reports.countByReportedAtAfter(since),
                incidentsLast24h,
                minutes(toConfirm),
                minutes(toResolve),
                byStatus,
                openByPriority,
                openByType,
                topSectors);
    }

    private Double minutes(OptionalDouble seconds) {
        if (seconds.isEmpty()) {
            return null;
        }
        return Math.round(seconds.getAsDouble() / 60.0 * 10.0) / 10.0;
    }
}
