package pe.edu.upc.luzdelsur.incidencias.embeddings;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Puerto hacia el microservicio de embeddings (FastAPI + sentence-transformers).
 * Se aisla detras de una interfaz para poder probar el motor sin red.
 */
public interface DescriptionSimilarityPort {

    OptionalDouble similarity(String textA, String textB);

    OptionalDouble maxSimilarity(String text, List<String> candidates);

    boolean isEnabled();

    boolean isAvailable();

    String status();
}
