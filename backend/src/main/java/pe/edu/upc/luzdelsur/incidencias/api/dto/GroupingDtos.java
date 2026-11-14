package pe.edu.upc.luzdelsur.incidencias.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;

public final class GroupingDtos {

    private GroupingDtos() {
    }

    public record PreviewRequest(
            @Size(max = 120) String sector,
            @NotNull FailureType failureType,
            @NotBlank @Size(max = 1000) String description,
            Instant reportedAt) {
    }

    public record PreviewResponse(
            boolean aggregate,
            UUID candidateIncidentId,
            Certainty certainty,
            boolean requiresReview,
            boolean sectorMatch,
            boolean timeMatch,
            boolean typeMatch,
            Double similarity,
            List<String> reasons,
            String clientMessage) {
    }

    public record GroupingConfigResponse(
            long windowMinutes,
            int maxCandidates,
            boolean embeddingsEnabled,
            boolean embeddingsAvailable,
            String embeddingsStatus,
            double matchThreshold,
            double conflictThreshold) {
    }
}
