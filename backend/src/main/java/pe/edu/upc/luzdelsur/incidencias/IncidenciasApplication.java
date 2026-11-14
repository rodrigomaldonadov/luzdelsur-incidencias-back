package pe.edu.upc.luzdelsur.incidencias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IncidenciasApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidenciasApplication.class, args);
    }
}
