package pe.edu.upc.luzdelsur.incidencias.embeddings;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import pe.edu.upc.luzdelsur.incidencias.config.AppProperties;

/**
 * Cliente HTTP del microservicio de embeddings.
 *
 * <p>Diseno tolerante a fallos: si el servicio no responde dentro del timeout,
 * se activa un enfriamiento y el motor heuristico continua solo con las reglas
 * declaradas (sector, tiempo y tipo). La disponibilidad del servicio nunca
 * bloquea el registro de un reporte.</p>
 */
@Component
public class HttpDescriptionSimilarityClient implements DescriptionSimilarityPort {

    private static final Logger log = LoggerFactory.getLogger(HttpDescriptionSimilarityClient.class);

    private final AppProperties properties;
    private final RestClient restClient;

    private volatile Instant blockedUntil = Instant.EPOCH;

    public HttpDescriptionSimilarityClient(AppProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        AppProperties.Embeddings config = properties.getEmbeddings();

        int timeoutMs = (int) Math.max(200, config.getTimeoutMs());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = builder
                .baseUrl(stripTrailingSlash(config.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public OptionalDouble similarity(String textA, String textB) {
        if (!isEnabled() || textA == null || textB == null) {
            return OptionalDouble.empty();
        }
        if (!isAvailable()) {
            return OptionalDouble.empty();
        }
        try {
            SimilarityResponse response = restClient.post()
                    .uri("/api/v1/similarity")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new SimilarityRequest(textA, textB))
                    .retrieve()
                    .body(SimilarityResponse.class);

            OptionalDouble value = sanitize(response == null ? null : response.similarity());
            if (value.isEmpty()) {
                log.warn("Respuesta invalida del microservicio de embeddings en /api/v1/similarity");
            }
            return value;
        } catch (RestClientException ex) {
            block(ex);
            return OptionalDouble.empty();
        }
    }

    @Override
    public OptionalDouble maxSimilarity(String text, List<String> candidates) {
        if (!isEnabled() || text == null || candidates == null || candidates.isEmpty()) {
            return OptionalDouble.empty();
        }
        if (!isAvailable()) {
            return OptionalDouble.empty();
        }
        try {
            SimilarityBatchResponse response = restClient.post()
                    .uri("/api/v1/similarity/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new SimilarityBatchRequest(text, candidates))
                    .retrieve()
                    .body(SimilarityBatchResponse.class);

            if (response == null || response.similarities() == null || response.similarities().isEmpty()) {
                log.warn("Respuesta vacia del microservicio de embeddings en /api/v1/similarity/batch");
                return OptionalDouble.empty();
            }

            OptionalDouble max = response.similarities().stream()
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .filter(value -> value >= 0.0 && value <= 1.0)
                    .max();

            if (max.isEmpty()) {
                log.warn("El microservicio de embeddings devolvio valores fuera del rango 0-1");
            }
            return max;
        } catch (RestClientException ex) {
            block(ex);
            return OptionalDouble.empty();
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.getEmbeddings().isEnabled();
    }

    @Override
    public boolean isAvailable() {
        return isEnabled() && !Instant.now().isBefore(blockedUntil);
    }

    @Override
    public String status() {
        if (!isEnabled()) {
            return "DESHABILITADO";
        }
        return isAvailable() ? "DISPONIBLE" : "EN_ESPERA";
    }

    private void block(RestClientException ex) {
        long cooldown = Math.max(1, properties.getEmbeddings().getCooldownSeconds());
        blockedUntil = Instant.now().plus(Duration.ofSeconds(cooldown));
        log.warn("Microservicio de embeddings no disponible ({}). Se omite la senal semantica por {} segundos",
                ex.getMessage(), cooldown);
    }

    private OptionalDouble sanitize(Double value) {
        if (value == null || value.isNaN() || value < 0.0 || value > 1.0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(value);
    }

    private String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8000";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    record SimilarityRequest(
            @JsonProperty("text_a") String textA,
            @JsonProperty("text_b") String textB) {
    }

    record SimilarityResponse(Double similarity) {
    }

    record SimilarityBatchRequest(String text, List<String> candidates) {
    }

    record SimilarityBatchResponse(List<Double> similarities) {
    }
}
