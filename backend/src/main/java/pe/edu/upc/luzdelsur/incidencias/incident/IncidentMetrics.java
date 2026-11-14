package pe.edu.upc.luzdelsur.incidencias.incident;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import pe.edu.upc.luzdelsur.incidencias.report.ReportRepository;

/**
 * Recalcula de forma centralizada contadores, certeza y prioridad de un caso.
 * Se invoca despues de vincular, separar o reasignar reportes.
 */
@Component
public class IncidentMetrics {

    private final ReportRepository reports;
    private final IncidentPriorityCalculator priorityCalculator;
    private final Clock clock;

    public IncidentMetrics(ReportRepository reports,
                           IncidentPriorityCalculator priorityCalculator,
                           Clock clock) {
        this.reports = reports;
        this.priorityCalculator = priorityCalculator;
        this.clock = clock;
    }

    public void refresh(Incident incident) {
        ReportRepository.ReportStats stats = reports.findStats(incident.getId());
        int total = (int) stats.getTotal();

        Certainty certainty = Certainty.ALTA;
        if (total > 0) {
            List<Certainty> certainties = reports.findLinkCertainties(incident.getId());
            certainty = Certainty.worstOf(certainties);
        }

        Instant now = clock.instant();
        PriorityScore priority = priorityCalculator.calculate(
                total, incident.getFailureType(), incident.getFirstReportedAt(), now);

        incident.updateMetrics(total, stats.getFirstReportedAt(), stats.getLastReportedAt(),
                certainty, priority, now);
    }
}
