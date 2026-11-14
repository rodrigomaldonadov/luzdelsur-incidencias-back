package pe.edu.upc.luzdelsur.incidencias.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.incident.PriorityLabel;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;
import pe.edu.upc.luzdelsur.incidencias.report.LinkAction;

public final class OperatorDtos {

    private OperatorDtos() {
    }

    public record IncidentSummary(
            UUID id,
            String sector,
            FailureType failureType,
            IncidentStatus status,
            Certainty certainty,
            boolean requiresReview,
            double priorityScore,
            PriorityLabel priorityLabel,
            int reportCount,
            Instant firstReportedAt,
            Instant lastReportedAt,
            Instant confirmedAt,
            String confirmedBy,
            Instant closedAt,
            Instant updatedAt,
            long version) {
    }

    public record LinkHistoryView(
            Long id,
            UUID reportId,
            UUID fromIncidentId,
            UUID toIncidentId,
            LinkAction action,
            String actorId,
            String note,
            Instant createdAt) {
    }

    public record IncidentDetail(
            IncidentSummary incident,
            List<ReportDtos.ReportView> reports,
            List<LinkHistoryView> history,
            List<String> alerts) {
    }

    public record ConfirmRequest(@Size(max = 500) String note) {
    }

    public record StatusRequest(
            @NotNull IncidentStatus status,
            @Size(max = 500) String note) {
    }

    public record SeparateRequest(
            Boolean createNewIncident,
            @Size(max = 500) String note) {
    }

    public record ReassignRequest(
            @NotNull UUID targetIncidentId,
            @Size(max = 500) String note) {
    }

    public record SeparationResult(
            IncidentDetail sourceIncident,
            UUID newIncidentId) {
    }
}
