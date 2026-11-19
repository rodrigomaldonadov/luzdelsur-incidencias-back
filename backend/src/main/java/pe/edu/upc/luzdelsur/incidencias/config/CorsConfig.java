package pe.edu.upc.luzdelsur.incidencias.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    public CorsConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = properties.getCors().getAllowedOrigins().toArray(String[]::new);

        // /v1/** se mantiene como alias de compatibilidad con el frontend ya
        // compilado; ApiPathAliasFilter lo reescribe a /api/v1/**.
        for (String path : new String[] {"/api/**", "/v1/**"}) {
            registry.addMapping(path)
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("Content-Type", "Authorization", "X-Actor-Id", "X-Actor-Role")
                    .maxAge(3600);
        }
    }
}
