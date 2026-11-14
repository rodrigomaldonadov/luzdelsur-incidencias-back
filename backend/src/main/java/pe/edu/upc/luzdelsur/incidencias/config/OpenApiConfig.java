package pe.edu.upc.luzdelsur.incidencias.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI incidenciasOpenApi() {
        return new OpenAPI().info(new Info()
                .title("API de gestion y priorizacion de incidencias electricas")
                .version("1.0.0")
                .description("""
                        Prototipo academico (Luz del Sur). El motor heuristico agrupa reportes por
                        sector, ventana temporal y tipo de falla, y deja la decision final al operador.
                        Los reportes originales se conservan siempre (relacion 1:N incidencia-reporte).
                        """)
                .contact(new Contact().name("Equipo Innovacion y Transformacion Digital - UPC")));
    }
}
