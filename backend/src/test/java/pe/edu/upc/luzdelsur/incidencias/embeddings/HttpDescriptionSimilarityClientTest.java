package pe.edu.upc.luzdelsur.incidencias.embeddings;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import pe.edu.upc.luzdelsur.incidencias.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el contrato HTTP con el microservicio de embeddings y la
 * degradacion controlada cuando el servicio falla.
 */
class HttpDescriptionSimilarityClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Batch: envia texto y candidatos, filtra valores fuera de rango y devuelve el maximo")
    void batchReturnsMaxValidSimilarity() throws IOException {
        startServer(200, """
                {"similarities":[0.20,0.85,1.5,-0.2]}
                """, "/api/v1/similarity/batch");

        HttpDescriptionSimilarityClient client = client();

        OptionalDouble result = client.maxSimilarity("no hay luz", List.of("corte", "apagon"));

        assertThat(result).hasValue(0.85);
        assertThat(lastRequestBody.get()).contains("\"text\"").contains("\"candidates\"");
    }

    @Test
    @DisplayName("Similitud simple: usa los nombres text_a y text_b del contrato")
    void singleSimilarityUsesSnakeCaseFields() throws IOException {
        startServer(200, """
                {"similarity":0.42}
                """, "/api/v1/similarity");

        HttpDescriptionSimilarityClient client = client();
        OptionalDouble result = client.similarity("corte de luz", "no hay energia");

        assertThat(result).hasValue(0.42);
        assertThat(lastRequestBody.get()).contains("\"text_a\"").contains("\"text_b\"");
    }

    @Test
    @DisplayName("Si el microservicio falla, se activa el enfriamiento y el motor continua")
    void failureActivatesCooldown() throws IOException {
        startServer(500, "{}", "/api/v1/similarity/batch");

        HttpDescriptionSimilarityClient client = client();

        assertThat(client.maxSimilarity("no hay luz", List.of("corte"))).isEmpty();
        assertThat(client.isAvailable()).isFalse();
        assertThat(client.status()).isEqualTo("EN_ESPERA");
        assertThat(client.maxSimilarity("otra vez", List.of("corte"))).isEmpty();
    }

    @Test
    @DisplayName("Deshabilitado: no realiza llamadas y reporta el estado")
    void disabledClientDoesNotCall() {
        AppProperties properties = new AppProperties();
        properties.getEmbeddings().setEnabled(false);
        HttpDescriptionSimilarityClient client = new HttpDescriptionSimilarityClient(
                properties, RestClient.builder());

        assertThat(client.maxSimilarity("texto", List.of("candidato"))).isEmpty();
        assertThat(client.status()).isEqualTo("DESHABILITADO");
    }

    private HttpDescriptionSimilarityClient client() {
        AppProperties properties = new AppProperties();
        properties.getEmbeddings().setEnabled(true);
        properties.getEmbeddings().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getEmbeddings().setTimeoutMs(1000);
        properties.getEmbeddings().setCooldownSeconds(60);
        return new HttpDescriptionSimilarityClient(properties, RestClient.builder());
    }

    private void startServer(int status, String responseBody, String path) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }
}
