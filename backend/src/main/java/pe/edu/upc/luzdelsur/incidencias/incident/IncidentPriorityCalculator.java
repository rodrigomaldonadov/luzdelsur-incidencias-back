package pe.edu.upc.luzdelsur.incidencias.incident;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import pe.edu.upc.luzdelsur.incidencias.report.FailureType;

/**
 * Priorizacion del prototipo (punto 4.5 del informe):
 * usuarios afectados, criticidad del tipo de falla y tiempo transcurrido.
 * No reemplaza los protocolos oficiales de despacho.
 */
@Component
public class IncidentPriorityCalculator {

    private static final double MAX_REPORTS_FOR_SCORE = 10.0;
    private static final double HOURS_FOR_FULL_ELAPSED_SCORE = 6.0;

    public PriorityScore calculate(int reportCount, FailureType failureType,
                                   Instant firstReportedAt, Instant now) {
        double affectedUsers = Math.min(reportCount, MAX_REPORTS_FOR_SCORE) / MAX_REPORTS_FOR_SCORE * 40.0;
        double criticality = failureType.priorityWeight() * 30.0;

        double elapsedHours = 0;
        if (firstReportedAt != null && firstReportedAt.isBefore(now)) {
            elapsedHours = Duration.between(firstReportedAt, now).toMinutes() / 60.0;
        }
        double elapsed = Math.min(elapsedHours / HOURS_FOR_FULL_ELAPSED_SCORE, 1.0) * 30.0;

        double score = Math.round((affectedUsers + criticality + elapsed) * 100.0) / 100.0;
        return new PriorityScore(score, labelFor(score));
    }

    private PriorityLabel labelFor(double score) {
        if (score >= 70.0) {
            return PriorityLabel.CRITICA;
        }
        if (score >= 50.0) {
            return PriorityLabel.ALTA;
        }
        if (score >= 30.0) {
            return PriorityLabel.MEDIA;
        }
        return PriorityLabel.BAJA;
    }
}
