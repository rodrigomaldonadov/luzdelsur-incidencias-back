package pe.edu.upc.luzdelsur.incidencias.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros configurables por variables de entorno.
 *
 * <p>Todas las reglas del motor heuristico son configurables para poder ejecutar
 * los casos de prueba con ventanas de 15, 30 y 60 minutos (punto 7.3.3 del informe)
 * sin recompilar.</p>
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String publicUrl = "http://localhost:8080";
    private final Cors cors = new Cors();
    private final Security security = new Security();
    private final Grouping grouping = new Grouping();
    private final Embeddings embeddings = new Embeddings();

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public Cors getCors() {
        return cors;
    }

    public Security getSecurity() {
        return security;
    }

    public Grouping getGrouping() {
        return grouping;
    }

    public Embeddings getEmbeddings() {
        return embeddings;
    }

    public static class Cors {

        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Security {

        private String devDefaultRole = "OPERADOR";

        public String getDevDefaultRole() {
            return devDefaultRole;
        }

        public void setDevDefaultRole(String devDefaultRole) {
            this.devDefaultRole = devDefaultRole;
        }
    }

    public static class Grouping {

        private long windowMinutes = 30;
        private int maxCandidates = 5;

        public long getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(long windowMinutes) {
            this.windowMinutes = windowMinutes;
        }

        public int getMaxCandidates() {
            return maxCandidates;
        }

        public void setMaxCandidates(int maxCandidates) {
            this.maxCandidates = maxCandidates;
        }
    }

    public static class Embeddings {

        private boolean enabled = true;
        private String baseUrl = "http://localhost:8000";
        private long timeoutMs = 1500;
        private long cooldownSeconds = 30;
        private double matchThreshold = 0.55;
        private double conflictThreshold = 0.30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public long getCooldownSeconds() {
            return cooldownSeconds;
        }

        public void setCooldownSeconds(long cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
        }

        public double getMatchThreshold() {
            return matchThreshold;
        }

        public void setMatchThreshold(double matchThreshold) {
            this.matchThreshold = matchThreshold;
        }

        public double getConflictThreshold() {
            return conflictThreshold;
        }

        public void setConflictThreshold(double conflictThreshold) {
            this.conflictThreshold = conflictThreshold;
        }
    }
}
