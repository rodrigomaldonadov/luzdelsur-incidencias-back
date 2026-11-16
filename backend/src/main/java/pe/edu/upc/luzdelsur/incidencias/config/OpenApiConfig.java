package pe.edu.upc.luzdelsur.incidencias.config;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
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

    /**
     * Cabeceras temporales hasta que Persona 3 implemente JWT. Permiten probar
     * los endpoints de cliente identificado y de tecnico desde Swagger UI.
     */
    @Bean
    public GlobalOpenApiCustomizer actorHeadersCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        List<Parameter> parameters = operation.getParameters() == null
                                ? new ArrayList<>()
                                : new ArrayList<>(operation.getParameters());
                        parameters.add(new Parameter()
                                .in("header")
                                .name("X-Actor-Id")
                                .required(false)
                                .description("TEMPORAL (hasta JWT): identificador del actor")
                                .schema(new StringSchema()));
                        parameters.add(new Parameter()
                                .in("header")
                                .name("X-Actor-Role")
                                .required(false)
                                .description("TEMPORAL (hasta JWT): CLIENTE, OPERADOR, TECNICO o SISTEMA")
                                .schema(new StringSchema()._enum(
                                        List.of("CLIENTE", "OPERADOR", "TECNICO", "SISTEMA"))));
                        operation.setParameters(parameters);
                    }));
        };
    }
}
