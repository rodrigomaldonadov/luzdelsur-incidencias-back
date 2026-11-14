package pe.edu.upc.luzdelsur.incidencias.grouping;

import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.upc.luzdelsur.incidencias.config.AppProperties;
import pe.edu.upc.luzdelsur.incidencias.embeddings.DescriptionSimilarityPort;
import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.incident.Incident;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de las reglas del motor, alineadas al punto 7.4 del informe (C1-C5).
 */
class GroupingEngineTest {

    private AppProperties properties;
    private GroupingEngine engine;
    private StubSimilarity similarity;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getGrouping().setWindowMinutes(30);
        properties.getEmbeddings().setEnabled(false);
        similarity = new StubSimilarity();
        engine = new GroupingEngine(properties, similarity);
    }

    @Test
    @DisplayName("C1: mismo sector, tiempo y tipo propone agrupacion con certeza alta")
    void c1GroupsSameSectorTimeAndType() {
        Incident existing = incident("SAN ISIDRO", FailureType.CORTE_TOTAL);
        GroupingProposal proposal = engine.propose("SAN ISIDRO", FailureType.CORTE_TOTAL,
                "No hay luz en toda la cuadra", Instant.now(),
                List.of(new GroupingCandidate(existing, List.of("Corte de luz general"))));

        assertThat(proposal.aggregates()).isTrue();
        assertThat(proposal.candidateIncidentId()).isEqualTo(existing.getId());
        assertThat(proposal.certainty()).isEqualTo(Certainty.ALTA);
        assertThat(proposal.requiresReview()).isFalse();
        assertThat(proposal.sectorMatch()).isTrue();
        assertThat(proposal.timeMatch()).isTrue();
        assertThat(proposal.typeMatch()).isTrue();
    }

    @Test
    @DisplayName("C2: mismo sector y tiempo con tipo distinto se marca Baja Certeza")
    void c2DifferentTypeIsLowCertainty() {
        Incident existing = incident("SAN ISIDRO", FailureType.CORTE_TOTAL);
        GroupingProposal proposal = engine.propose("SAN ISIDRO", FailureType.BAJA_TENSION,
                "La luz parpadea y baja la tension", Instant.now(),
                List.of(new GroupingCandidate(existing, List.of("Corte total"))));

        assertThat(proposal.aggregates()).isTrue();
        assertThat(proposal.certainty()).isEqualTo(Certainty.BAJA);
        assertThat(proposal.requiresReview()).isTrue();
        assertThat(proposal.typeMatch()).isFalse();
    }

    @Test
    @DisplayName("C3 y C4: sin casos candidatos se crea una incidencia nueva")
    void c3c4NoCandidatesCreatesNewIncident() {
        GroupingProposal proposal = engine.propose("MIRAFLORES", FailureType.CORTE_TOTAL,
                "Sin suministro desde hace una hora", Instant.now(), List.of());

        assertThat(proposal.aggregates()).isFalse();
        assertThat(proposal.candidateIncidentId()).isNull();
        assertThat(proposal.certainty()).isEqualTo(Certainty.ALTA);
        assertThat(proposal.sectorMatch()).isFalse();
        assertThat(proposal.timeMatch()).isFalse();
    }

    @Test
    @DisplayName("C5: ubicacion incompleta crea caso con Baja Certeza")
    void c5UnknownSectorIsLowCertainty() {
        GroupingProposal proposal = engine.propose(null, FailureType.CORTE_TOTAL,
                "No tengo luz", Instant.now(), List.of());

        assertThat(proposal.aggregates()).isFalse();
        assertThat(proposal.certainty()).isEqualTo(Certainty.BAJA);
        assertThat(proposal.requiresReview()).isTrue();
    }

    @Test
    @DisplayName("C5 con candidato: ubicacion incompleta se vincula de forma tentativa")
    void c5UnknownSectorLinksTentatively() {
        Incident existing = incident("SIN SECTOR", FailureType.CORTE_TOTAL);
        GroupingProposal proposal = engine.propose(null, FailureType.CORTE_TOTAL,
                "No tengo luz", Instant.now(),
                List.of(new GroupingCandidate(existing, List.of("Sin luz"))));

        assertThat(proposal.aggregates()).isTrue();
        assertThat(proposal.certainty()).isEqualTo(Certainty.BAJA);
        assertThat(proposal.requiresReview()).isTrue();
    }

    @Test
    @DisplayName("Descripcion semanticamente distinta degrada la certeza a Baja")
    void lowSimilarityDowngradesCertainty() {
        properties.getEmbeddings().setEnabled(true);
        similarity.value = OptionalDouble.of(0.10);

        Incident existing = incident("SAN ISIDRO", FailureType.CORTE_TOTAL);
        GroupingProposal proposal = engine.propose("SAN ISIDRO", FailureType.CORTE_TOTAL,
                "Se fue la luz", Instant.now(),
                List.of(new GroupingCandidate(existing, List.of("Falla del medidor"))));

        assertThat(proposal.certainty()).isEqualTo(Certainty.BAJA);
        assertThat(proposal.requiresReview()).isTrue();
        assertThat(proposal.similarity()).isEqualTo(0.10);
    }

    @Test
    @DisplayName("Microservicio de embeddings caido: las reglas siguen operando con certeza media")
    void embeddingsUnavailableKeepsRulesWorking() {
        properties.getEmbeddings().setEnabled(true);
        similarity.value = OptionalDouble.empty();

        Incident existing = incident("SAN ISIDRO", FailureType.CORTE_TOTAL);
        GroupingProposal proposal = engine.propose("SAN ISIDRO", FailureType.CORTE_TOTAL,
                "Se fue la luz", Instant.now(),
                List.of(new GroupingCandidate(existing, List.of("Corte total"))));

        assertThat(proposal.aggregates()).isTrue();
        assertThat(proposal.certainty()).isEqualTo(Certainty.MEDIA);
        assertThat(proposal.similarity()).isNull();
    }

    private Incident incident(String sectorKey, FailureType type) {
        return Incident.start(UUID.randomUUID(), sectorKey, sectorKey, type,
                Instant.now().minusSeconds(300), Certainty.ALTA, Instant.now());
    }

    private static final class StubSimilarity implements DescriptionSimilarityPort {

        private OptionalDouble value = OptionalDouble.empty();

        @Override
        public OptionalDouble similarity(String textA, String textB) {
            return value;
        }

        @Override
        public OptionalDouble maxSimilarity(String text, List<String> candidates) {
            return value;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String status() {
            return "DISPONIBLE";
        }
    }
}
