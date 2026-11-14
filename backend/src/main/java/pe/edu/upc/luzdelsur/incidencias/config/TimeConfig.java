package pe.edu.upc.luzdelsur.incidencias.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /**
     * Reloj inyectable para que las pruebas del motor sean deterministas.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
