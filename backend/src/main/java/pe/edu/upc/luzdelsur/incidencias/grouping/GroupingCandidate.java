package pe.edu.upc.luzdelsur.incidencias.grouping;

import java.util.List;
import java.util.UUID;

import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.incident.Incident;

public record GroupingCandidate(Incident incident, List<String> descriptions) {
}
