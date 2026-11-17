package pe.edu.upc.luzdelsur.incidencias.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.incident.PriorityLabel;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;
import pe.edu.upc.luzdelsur.incidencias.report.ReportLinkStatus;

public final class TechnicianDtos {

    private TechnicianDtos() {
    }

    /**
     * Fila de la bandeja del tecnico: casos confirmados, en atencion o
     * reabiertos. No incluye casos sugeridos ni descartados.
     */
    public record TechnicianIncidentSummary(
            UUID id,
            String sector,
            FailureType failureType,
            IncidentStatus status,
            Certainty certainty,
            boolean requiresReview,
            PriorityLabel priorityLabel,
            double priorityScore,
            int affectedReports,
            Instant firstReportedAt,
            Instant lastReportedAt,
            String assignedTechnicianId,
            Instant assignedAt,
            String estimatedScope,
            Instant statusUpdatedAt,
            Instant closedAt,
            Instant updatedAt) {
    }

    public record TechnicianReportView(
            UUID id,
            String sector,
            String reference,
            FailureType failureType,
            String description,
            Instant reportedAt,
            ReportLinkStatus linkStatus,
            Certainty certainty,
            BigDecimal latitude,
            BigDecimal longitude) {
    }

    public record TechnicianIncidentView(
            UUID id,
            String sector,
            FailureType failureType,
            IncidentStatus status,
            Certainty certainty,
            boolean requiresReview,
            PriorityLabel priorityLabel,
            double priorityScore,
            int affectedReports,
            Instant firstReportedAt,
            Instant lastReportedAt,
            Instant confirmedAt,
            String confirmedBy,
            Instant statusUpdatedAt,
            String statusUpdatedBy,
            String assignedTechnicianId,
            Instant assignedAt,
            Instant closedAt,
            Instant updatedAt,
            String estimatedScope,
            List<TechnicianReportView> reports,
            List<String> warnings) {
    }
}
