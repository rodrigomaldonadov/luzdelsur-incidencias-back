package pe.edu.upc.luzdelsur.incidencias.grouping;

import java.util.List;
import java.util.UUID;

import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;

public record GroupingProposal(
        GroupingDecision decision,
        UUID candidateIncidentId,
        Certainty certainty,
        boolean requiresReview,
        boolean sectorMatch,
        boolean timeMatch,
        boolean typeMatch,
        Double similarity,
        List<String> reasons) {

    public boolean aggregates() {
        return decision == GroupingDecision.VINCULAR;
    }

    public String reasonsAsText() {
        return String.join("; ", reasons);
    }
}
