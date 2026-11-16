package pe.edu.upc.luzdelsur.incidencias.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;
import pe.edu.upc.luzdelsur.incidencias.report.ReportLinkStatus;

public final class ReportDtos {

    private ReportDtos() {
    }

    /**
     * El sector es opcional a proposito: el caso C5 del informe exige probar
     * reportes con ubicacion incompleta (se marcan como Baja Certeza).
     */
    public record CreateRequest(
            @Size(max = 64) String clientId,
            @Size(max = 120) String sector,
            @Size(max = 200) String reference,
            @NotNull FailureType failureType,
            @NotBlank @Size(max = 1000) String description,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            Instant reportedAt) {
    }

    public record ReportView(
            UUID id,
            String sector,
            String reference,
            FailureType failureType,
            String description,
            Instant reportedAt,
            UUID incidentId,
            ReportLinkStatus linkStatus,
            Certainty certainty,
            String groupingReason,
            Double similarity,
            Instant linkUpdatedAt) {
    }

    public record CreateResponse(
            ReportView report,
            UUID incidentId,
            IncidentStatus incidentStatus,
            Certainty certainty,
            boolean requiresReview,
            String clientMessage,
            List<String> reasons) {
    }

    public record TrackingResponse(
            ReportView report,
            UUID incidentId,
            IncidentStatus incidentStatus,
            Certainty certainty,
            boolean requiresReview,
            Instant incidentUpdatedAt,
            String clientMessage,
            boolean canRate,
            FeedbackView feedback) {
    }

    /** Calificacion de la atencion (1 a 5), solo con el caso RESUELTA o CERRADA. */
    public record FeedbackRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 500) String comment) {
    }

    public record FeedbackView(
            int rating,
            String comment,
            Instant createdAt) {
    }

    public record PersistenceRequest(@Size(max = 500) String note) {
    }
}
