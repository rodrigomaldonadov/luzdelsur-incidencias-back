package pe.edu.upc.luzdelsur.incidencias.grouping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

import org.springframework.stereotype.Component;

import pe.edu.upc.luzdelsur.incidencias.config.AppProperties;
import pe.edu.upc.luzdelsur.incidencias.embeddings.DescriptionSimilarityPort;
import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;

/**
 * Motor de agrupacion heuristica.
 *
 * <p>Reglas declaradas (punto 4.5 del informe):</p>
 * <ol>
 *   <li>Proximidad por sector o cuadrante declarado.</li>
 *   <li>Ventana temporal configurable.</li>
 *   <li>Coincidencia de tipo de falla.</li>
 * </ol>
 *
 * <p>La similitud semantica del microservicio de embeddings es una senal
 * auxiliar: las reglas siguen siendo la base de la decision y la palabra final
 * es del operador. Los casos dudosos se marcan como {@link Certainty#BAJA}.</p>
 */
@Component
public class GroupingEngine {

    private final AppProperties properties;
    private final DescriptionSimilarityPort similarityPort;

    public GroupingEngine(AppProperties properties, DescriptionSimilarityPort similarityPort) {
        this.properties = properties;
        this.similarityPort = similarityPort;
    }

    public GroupingProposal propose(String sectorKey, FailureType failureType, String description,
                                    Instant reportedAt, List<GroupingCandidate> candidates) {
        long windowMinutes = properties.getGrouping().getWindowMinutes();

        if (sectorKey == null) {
            return proposeWithUnknownSector(candidates, windowMinutes);
        }

        if (candidates.isEmpty()) {
            return new GroupingProposal(
                    GroupingDecision.NUEVA_INCIDENCIA, null, Certainty.ALTA, false,
                    false, false, false, null,
                    List.of("Sin casos abiertos en el mismo sector dentro de " + windowMinutes + " minutos",
                            "Se crea una incidencia nueva para el reporte"));
        }

        GroupingCandidate best = selectBestCandidate(candidates, failureType);
        boolean typeMatch = best.incident().getFailureType() == failureType;

        List<String> reasons = new ArrayList<>();
        reasons.add("Sector coincidente: " + best.incident().getSector());
        reasons.add("Dentro de la ventana de " + windowMinutes + " minutos");

        if (!typeMatch) {
            reasons.add("Tipo de falla distinto: caso abierto " + best.incident().getFailureType()
                    + " vs reporte " + failureType);
            reasons.add("Se marca Baja Certeza y se exige revision explicita del operador");
            return new GroupingProposal(
                    GroupingDecision.VINCULAR, best.incident().getId(), Certainty.BAJA, true,
                    true, true, false, null, List.copyOf(reasons));
        }

        reasons.add("Tipo de falla coincidente: " + failureType);

        if (!properties.getEmbeddings().isEnabled()) {
            reasons.add("Verificacion semantica deshabilitada: se aplican solo las reglas declaradas");
            return new GroupingProposal(
                    GroupingDecision.VINCULAR, best.incident().getId(), Certainty.ALTA, false,
                    true, true, true, null, List.copyOf(reasons));
        }

        OptionalDouble similarity = similarityPort.maxSimilarity(description, best.descriptions());
        if (similarity.isEmpty()) {
            reasons.add("Microservicio de embeddings no disponible: la certeza se degrada a media");
            return new GroupingProposal(
                    GroupingDecision.VINCULAR, best.incident().getId(), Certainty.MEDIA, false,
                    true, true, true, null, List.copyOf(reasons));
        }

        double value = round(similarity.getAsDouble());
        double matchThreshold = properties.getEmbeddings().getMatchThreshold();
        double conflictThreshold = properties.getEmbeddings().getConflictThreshold();

        if (value >= matchThreshold) {
            reasons.add("Similitud semantica " + format(value) + " (umbral de coincidencia " + format(matchThreshold) + ")");
            return new GroupingProposal(
                    GroupingDecision.VINCULAR, best.incident().getId(), Certainty.ALTA, false,
                    true, true, true, value, List.copyOf(reasons));
        }

        if (value <= conflictThreshold) {
            reasons.add("Similitud semantica " + format(value) + " por debajo del umbral de conflicto "
                    + format(conflictThreshold) + ": posible falso positivo");
            return new GroupingProposal(
                    GroupingDecision.VINCULAR, best.incident().getId(), Certainty.BAJA, true,
                    true, true, true, value, List.copyOf(reasons));
        }

        reasons.add("Similitud semantica intermedia " + format(value) + ": confirmar manualmente");
        return new GroupingProposal(
                GroupingDecision.VINCULAR, best.incident().getId(), Certainty.MEDIA, false,
                true, true, true, value, List.copyOf(reasons));
    }

    private GroupingProposal proposeWithUnknownSector(List<GroupingCandidate> candidates, long windowMinutes) {
        if (candidates.isEmpty()) {
            return new GroupingProposal(
                    GroupingDecision.NUEVA_INCIDENCIA, null, Certainty.BAJA, true,
                    false, false, false, null,
                    List.of("Ubicacion incompleta o sector ambiguo: no es posible comparar el sector",
                            "Se crea un caso marcado como Baja Certeza para revision del operador"));
        }

        GroupingCandidate best = candidates.get(0);
        return new GroupingProposal(
                GroupingDecision.VINCULAR, best.incident().getId(), Certainty.BAJA, true,
                false, true, true, null,
                List.of("Ubicacion incompleta o sector ambiguo",
                        "Vinculacion tentativa al caso abierto mas reciente del mismo tipo dentro de "
                                + windowMinutes + " minutos",
                        "Requiere revision explicita del operador antes de consolidar"));
    }

    private GroupingCandidate selectBestCandidate(List<GroupingCandidate> candidates, FailureType failureType) {
        return candidates.stream()
                .filter(candidate -> candidate.incident().getFailureType() == failureType)
                .findFirst()
                .orElse(candidates.get(0));
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
