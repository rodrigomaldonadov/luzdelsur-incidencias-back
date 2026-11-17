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
    private final Auth auth = new Auth();
    private final Demo demo = new Demo();

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

    public Auth getAuth() {
        return auth;
    }

    public Demo getDemo() {
        return demo;
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

        /**
         * Rol que reciben las peticiones sin identidad cuando las cabeceras de
         * desarrollo estan habilitadas. Con JWT estricto, el anonimo es CLIENTE.
         */
        private String devDefaultRole = "OPERADOR";

        /**
         * Acepta X-Actor-Id / X-Actor-Role sin token. Solo para pruebas locales
         * y Swagger: en despliegue debe quedar en false.
         */
        private boolean allowDevHeaders = false;

        private final Jwt jwt = new Jwt();

        public String getDevDefaultRole() {
            return devDefaultRole;
        }

        public void setDevDefaultRole(String devDefaultRole) {
            this.devDefaultRole = devDefaultRole;
        }

        public boolean isAllowDevHeaders() {
            return allowDevHeaders;
        }

        public void setAllowDevHeaders(boolean allowDevHeaders) {
            this.allowDevHeaders = allowDevHeaders;
        }

        public Jwt getJwt() {
            return jwt;
        }
    }

    public static class Jwt {

        /** Secreto HS256; minimo 32 bytes. Vacio = secreto aleatorio por arranque. */
        private String secret = "";
        private String issuer = "luzdelsur-incidencias";
        private long accessTtlMinutes = 60;
        private long refreshTtlDays = 7;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public long getAccessTtlMinutes() {
            return accessTtlMinutes;
        }

        public void setAccessTtlMinutes(long accessTtlMinutes) {
            this.accessTtlMinutes = accessTtlMinutes;
        }

        public long getRefreshTtlDays() {
            return refreshTtlDays;
        }

        public void setRefreshTtlDays(long refreshTtlDays) {
            this.refreshTtlDays = refreshTtlDays;
        }
    }

    public static class Auth {

        /**
         * Si es false, el registro publico solo crea cuentas CLIENTE. Las cuentas
         * de operador y tecnico se crean con la semilla de demo o por un operador.
         */
        private boolean allowStaffRegistration = false;

        public boolean isAllowStaffRegistration() {
            return allowStaffRegistration;
        }

        public void setAllowStaffRegistration(boolean allowStaffRegistration) {
            this.allowStaffRegistration = allowStaffRegistration;
        }
    }

    public static class Demo {

        /** Crea las cuentas de demostracion si no existen. */
        private boolean seedUsers = false;
        /** Carga el dataset C1-C7 si la tabla de reportes esta vacia. */
        private boolean seedReports = false;
        private String password = "";

        public boolean isSeedUsers() {
            return seedUsers;
        }

        public void setSeedUsers(boolean seedUsers) {
            this.seedUsers = seedUsers;
        }

        public boolean isSeedReports() {
            return seedReports;
        }

        public void setSeedReports(boolean seedReports) {
            this.seedReports = seedReports;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
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
