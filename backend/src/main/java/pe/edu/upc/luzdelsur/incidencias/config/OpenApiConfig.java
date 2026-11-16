package pe.edu.upc.luzdelsur.incidencias.config;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI incidenciasOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de gestion y priorizacion de incidencias electricas")
                        .version("1.1.0")
                        .description("""
                                Prototipo academico (Luz del Sur). El motor heuristico agrupa reportes por
                                sector, ventana temporal y tipo de falla, y deja la decision final al operador.
                                Los reportes originales se conservan siempre (relacion 1:N incidencia-reporte).

                                Autenticacion: POST /api/v1/auth/login devuelve un JWT. Use el boton
                                "Authorize" con ese token para probar los endpoints de operador y tecnico.
                                """)
                        .contact(new Contact().name("Equipo Innovacion y Transformacion Digital - UTP")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * Cabeceras de desarrollo. Solo se documentan si el backend las acepta
     * ({@code APP_ALLOW_DEV_HEADERS=true}); en despliegue la identidad es el JWT.
     */
    @Bean
    public GlobalOpenApiCustomizer actorHeadersCustomizer(AppProperties properties) {
        return openApi -> {
            if (!properties.getSecurity().isAllowDevHeaders() || openApi.getPaths() == null) {
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
                                .description("SOLO DESARROLLO (sin token): identificador del actor")
                                .schema(new StringSchema()));
                        parameters.add(new Parameter()
                                .in("header")
                                .name("X-Actor-Role")
                                .required(false)
                                .description("SOLO DESARROLLO (sin token): CLIENTE, OPERADOR, TECNICO o SISTEMA")
                                .schema(new StringSchema()._enum(
                                        List.of("CLIENTE", "OPERADOR", "TECNICO", "SISTEMA"))));
                        operation.setParameters(parameters);
                    }));
        };
    }
}
