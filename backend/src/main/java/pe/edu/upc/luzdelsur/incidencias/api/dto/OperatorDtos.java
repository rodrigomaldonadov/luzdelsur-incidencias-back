package pe.edu.upc.luzdelsur.incidencias.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatusEvent;
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
            String assignedTechnicianId,
            Instant assignedAt,
            Instant closedAt,
            Instant updatedAt,
            long version,
            String confirmedByName,
            String assignedTechnicianName) {
    }

    public record LinkHistoryView(
            Long id,
            UUID reportId,
            UUID fromIncidentId,
            UUID toIncidentId,
            LinkAction action,
            String actorId,
            String note,
            Instant createdAt,
            String actorName) {
    }

    /**
     * Evento del ciclo de vida del caso (confirmacion, cambio de estado o
     * asignacion), con el actor, su rol y la nota registrada.
     */
    public record StatusEventView(
            Long id,
            IncidentStatusEvent.EventType eventType,
            IncidentStatus fromStatus,
            IncidentStatus toStatus,
            String actorId,
            String actorName,
            String actorRole,
            String note,
            Instant createdAt) {
    }

    public record IncidentDetail(
            IncidentSummary incident,
            List<ReportDtos.ReportView> reports,
            List<LinkHistoryView> history,
            List<String> alerts,
            List<StatusEventView> statusHistory) {
    }

    /** Asignacion manual de un tecnico por el operador. */
    public record AssignRequest(
            @NotBlank @Size(max = 64) String technicianId,
            @Size(max = 500) String note) {
    }

    /** Indicadores del panel de monitoreo del operador. */
    public record MetricsView(
            Instant generatedAt,
            long openIncidents,
            long pendingReview,
            long lowCertaintyOpen,
            long unassignedConfirmed,
            long reportsLast24h,
            long incidentsLast24h,
            Double avgMinutesToConfirm,
            Double avgMinutesToResolve,
            java.util.Map<IncidentStatus, Long> byStatus,
            java.util.Map<PriorityLabel, Long> openByPriority,
            java.util.Map<FailureType, Long> openByFailureType,
            List<SectorCount> topSectors) {
    }

    public record SectorCount(String sector, long openIncidents, long reports) {
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
