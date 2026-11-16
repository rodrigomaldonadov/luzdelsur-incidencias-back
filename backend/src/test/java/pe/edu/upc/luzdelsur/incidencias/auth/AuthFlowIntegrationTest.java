package pe.edu.upc.luzdelsur.incidencias.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autenticacion en modo estricto (como en despliegue): las cabeceras X-Actor-*
 * se ignoran y la identidad sale solo del JWT.
 */
@SpringBootTest(properties = {
        "app.security.allow-dev-headers=false",
        "spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "clave-segura-1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("Registro, login, /me y refresh; el registro publico no crea personal")
    void registerLoginMeRefresh() throws Exception {
        JsonNode registered = register("Ana Cliente", "ana@correo.pe", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CLIENTE"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
                .transform(this::readTree);

        String token = registered.path("token").asText();
        String refreshToken = registered.path("refreshToken").asText();

        mvc.perform(bearer(get("/api/v1/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana@correo.pe"))
                .andExpect(jsonPath("$.name").value("Ana Cliente"));

        register("Otra Ana", "ANA@correo.pe", null)
                .andExpect(status().isConflict());

        register("Falso Operador", "falso@correo.pe", "OPERADOR")
                .andExpect(status().isForbidden());

        login("ana@correo.pe", "incorrecta", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        login("ana@correo.pe", PASSWORD, "TECNICO")
                .andExpect(status().isForbidden());

        login("  Ana@Correo.pe ", PASSWORD, "CLIENTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // Un token de acceso no sirve como token de refresco.
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(bearer(get("/api/v1/auth/me"), "no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sin token, las cabeceras X-Actor-* no otorgan permisos de operador")
    void devHeadersAreIgnoredInStrictMode() throws Exception {
        mvc.perform(get("/api/v1/operator/incidents")
                        .header("X-Actor-Id", "op-1")
                        .header("X-Actor-Role", "OPERADOR"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/reports/mine")
                        .header("X-Actor-Id", "cliente-uno"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Flujo con JWT: mis reportes, asignacion, tecnico, historial, calificacion, metricas y auditoria")
    void fullFlowWithJwt() throws Exception {
        String clientToken = tokenOf(register("Luis Cliente", "luis@correo.pe", null));
        String otherClientToken = tokenOf(register("Rosa Cliente", "rosa@correo.pe", null));

        authService.createUser("Operador Uno", "op1@luzdelsur.com.pe", PASSWORD, ActorRole.OPERADOR);
        AppUser tech = authService.createUser("Tecnico Uno", "tec1@luzdelsur.com.pe", PASSWORD, ActorRole.TECNICO);
        authService.createUser("Tecnico Dos", "tec2@luzdelsur.com.pe", PASSWORD, ActorRole.TECNICO);

        String operatorToken = tokenOf(login("op1@luzdelsur.com.pe", PASSWORD, "OPERADOR"));
        String techToken = tokenOf(login("tec1@luzdelsur.com.pe", PASSWORD, "TECNICO"));
        String tech2Token = tokenOf(login("tec2@luzdelsur.com.pe", PASSWORD, "TECNICO"));

        Instant base = Instant.now().minusSeconds(600);
        JsonNode first = createReport(clientToken, "Jesus Maria", "Sin luz en el jiron", base, "cliente-falso");
        createReport(otherClientToken, "Jesus Maria", "Corte en toda la cuadra", base.plusSeconds(60), null);
        String reportId = first.path("report").path("id").asText();
        String incidentId = first.path("incidentId").asText();

        // Mis reportes: solo los del sub del token, aunque el cuerpo traiga otro clientId.
        JsonNode mine = readTree(mvc.perform(bearer(get("/api/v1/reports/mine"), clientToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(mine.path("totalElements").asLong()).isEqualTo(1);
        assertThat(mine.path("content").get(0).path("report").path("id").asText()).isEqualTo(reportId);

        // El cliente no entra a la bandeja del operador.
        mvc.perform(bearer(get("/api/v1/operator/incidents"), clientToken))
                .andExpect(status().isForbidden());

        mvc.perform(bearer(post("/api/v1/operator/incidents/{id}/confirm", incidentId), operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Verificado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("CONFIRMADA"))
                .andExpect(jsonPath("$.statusHistory[0].actorName").value("Operador Uno"));

        mvc.perform(bearer(get("/api/v1/operator/technicians"), operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'tec1@luzdelsur.com.pe')]").exists());

        mvc.perform(bearer(post("/api/v1/operator/incidents/{id}/assign", incidentId), operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":\"" + tech.getId() + "\",\"note\":\"Cuadrilla cercana\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.assignedTechnicianId").value(tech.getId().toString()));

        mvc.perform(bearer(post("/api/v1/operator/incidents/{id}/assign", incidentId), operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(bearer(get("/api/v1/technician/incidents"), techToken).param("onlyMine", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(incidentId));

        // Otro tecnico no puede tomar un caso asignado.
        changeStatus(tech2Token, incidentId, "EN_ATENCION", null)
                .andExpect(status().isConflict());

        changeStatus(techToken, incidentId, "EN_ATENCION", "En camino").andExpect(status().isOk());
        changeStatus(techToken, incidentId, "RESUELTA", "Cambio de fusible en la subestacion")
                .andExpect(status().isOk());

        mvc.perform(bearer(get("/api/v1/technician/incidents/history"), techToken).param("onlyMine", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(incidentId))
                .andExpect(jsonPath("$.content[0].status").value("RESUELTA"));

        mvc.perform(bearer(get("/api/v1/technician/incidents/{id}/timeline", incidentId), techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory[?(@.note == 'Cambio de fusible en la subestacion')]").exists())
                .andExpect(jsonPath("$.statusHistory[?(@.eventType == 'ASIGNACION')]").exists());

        // Calificacion: solo el dueno, una vez, con el caso resuelto.
        rate(otherClientToken, reportId, 5).andExpect(status().isForbidden());
        rate(clientToken, reportId, 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.rating").value(5))
                .andExpect(jsonPath("$.canRate").value(false));
        rate(clientToken, reportId, 4).andExpect(status().isConflict());

        mvc.perform(bearer(get("/api/v1/operator/metrics"), operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.RESUELTA").value(1))
                .andExpect(jsonPath("$.avgMinutesToConfirm").isNumber());

        mvc.perform(bearer(get("/api/v1/operator/audit-events"), operatorToken).param("action", "LOGIN_OK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

        // Un tecnico desactivado ya no inicia sesion.
        mvc.perform(bearer(patch("/api/v1/operator/technicians/{id}", tech.getId()), operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        login("tec1@luzdelsur.com.pe", PASSWORD, "TECNICO").andExpect(status().isForbidden());
    }

    private ResultActions register(String name, String username, String role) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        body.put("username", username);
        body.put("password", PASSWORD);
        if (role != null) {
            body.put("role", role);
        }
        return mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions login(String username, String password, String role) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("username", username);
        body.put("password", password);
        if (role != null) {
            body.put("role", role);
        }
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String tokenOf(ResultActions actions) throws Exception {
        String body = actions.andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readTree(body).path("token").asText();
    }

    private JsonNode createReport(String token, String sector, String description, Instant reportedAt,
                                  String clientIdInBody) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("sector", sector);
        body.put("failureType", "CORTE_TOTAL");
        body.put("description", description);
        body.put("reportedAt", reportedAt.toString());
        if (clientIdInBody != null) {
            body.put("clientId", clientIdInBody);
        }
        String response = mvc.perform(bearer(post("/api/v1/reports"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return readTree(response);
    }

    private ResultActions changeStatus(String token, String incidentId, String target, String note) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("status", target);
        if (note != null) {
            body.put("note", note);
        }
        return mvc.perform(bearer(post("/api/v1/technician/incidents/{id}/status", incidentId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions rate(String token, String reportId, int rating) throws Exception {
        return mvc.perform(bearer(post("/api/v1/reports/{id}/feedback", reportId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":" + rating + ",\"comment\":\"Buena atencion\"}"));
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
