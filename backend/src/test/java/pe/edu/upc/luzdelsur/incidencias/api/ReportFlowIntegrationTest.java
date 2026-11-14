package pe.edu.upc.luzdelsur.incidencias.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.edu.upc.luzdelsur.incidencias.report.FailureType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Flujo completo: agrupacion, confirmacion, atencion, reapertura, separacion y reasignacion")
    void fullIncidentFlow() throws Exception {
        Instant base = Instant.now().minusSeconds(600);

        JsonNode first = createReport("San Isidro", FailureType.CORTE_TOTAL,
                "Corte total de luz en toda la cuadra", base);
        String incidentId = first.path("incidentId").asText();
        assertThat(first.path("certainty").asText()).isEqualTo("ALTA");
        assertThat(first.path("incidentStatus").asText()).isEqualTo("SUGERIDA");

        JsonNode second = createReport("san isidro", FailureType.CORTE_TOTAL,
                "No hay energia electrica en mi vivienda", base.plusSeconds(120));
        assertThat(second.path("incidentId").asText()).isEqualTo(incidentId);
        assertThat(second.path("certainty").asText()).isEqualTo("ALTA");

        // El rol por defecto en pruebas es CLIENTE: la bandeja del operador queda protegida.
        mvc.perform(get("/api/v1/operator/incidents"))
                .andExpect(status().isForbidden());

        String queueBody = mvc.perform(asOperator(get("/api/v1/operator/incidents")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode queue = objectMapper.readTree(queueBody);
        assertThat(queue.path("content").findValuesAsText("id")).contains(incidentId);

        mvc.perform(asOperator(post("/api/v1/operator/incidents/{id}/confirm", incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Agrupacion verificada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.reports[0].linkStatus").value("CONFIRMADO"))
                .andExpect(jsonPath("$.history[?(@.action == 'CONFIRMADO')]").exists());

        mvc.perform(get("/api/v1/technician/incidents/{id}", incidentId)
                        .header("X-Actor-Id", "tec-1")
                        .header("X-Actor-Role", "TECNICO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedReports").value(2))
                .andExpect(jsonPath("$.reports.length()").value(2))
                .andExpect(jsonPath("$.estimatedScope").isNotEmpty());

        changeTechnicianStatus(incidentId, "EN_ATENCION")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.status").value("EN_ATENCION"));

        changeTechnicianStatus(incidentId, "RESUELTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.status").value("RESUELTA"));

        String secondReportId = second.path("report").path("id").asText();
        mvc.perform(post("/api/v1/reports/{id}/persistence", secondReportId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"La luz sigue sin volver\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentStatus").value("REABIERTA"))
                .andExpect(jsonPath("$.clientMessage").isNotEmpty());

        String separationBody = mvc.perform(asOperator(
                                post("/api/v1/operator/reports/{id}/separate", secondReportId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"createNewIncident\":true,\"note\":\"Podria ser otra falla\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceIncident.incident.reportCount").value(1))
                .andExpect(jsonPath("$.newIncidentId").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String newIncidentId = objectMapper.readTree(separationBody).path("newIncidentId").asText();

        JsonNode tracking = getReport(secondReportId);
        assertThat(tracking.path("incidentId").asText()).isEqualTo(newIncidentId);

        // El reporte original sigue existiendo y puede reasignarse sin perder su UUID.
        mvc.perform(asOperator(post("/api/v1/operator/reports/{id}/reassign", secondReportId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIncidentId\":\"" + incidentId + "\",\"note\":\"Reintegrado al caso\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.id").value(incidentId))
                .andExpect(jsonPath("$.incident.reportCount").value(2));

        JsonNode history = getReport(secondReportId);
        assertThat(history.path("report").path("id").asText()).isEqualTo(secondReportId);
    }

    @Test
    @DisplayName("C2: mismo sector y tiempo con distinto tipo de falla queda en Baja Certeza")
    void c2LowCertainty() throws Exception {
        Instant base = Instant.now().minusSeconds(300);

        JsonNode first = createReport("Barranco", FailureType.CORTE_TOTAL,
                "Corte total de energia", base);
        JsonNode second = createReport("Barranco", FailureType.BAJA_TENSION,
                "La tension baja y los focos parpadean", base.plusSeconds(60));

        assertThat(second.path("incidentId").asText()).isEqualTo(first.path("incidentId").asText());
        assertThat(second.path("certainty").asText()).isEqualTo("BAJA");
        assertThat(second.path("requiresReview").asBoolean()).isTrue();

        mvc.perform(asOperator(get("/api/v1/operator/incidents"))
                        .param("requiresReview", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].certainty").value("BAJA"))
                .andExpect(jsonPath("$.content[0].requiresReview").value(true));

        mvc.perform(asOperator(post("/api/v1/grouping/preview"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Barranco","failureType":"BAJA_TENSION",
                                 "description":"Sigue bajando la tension"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregate").value(true))
                .andExpect(jsonPath("$.requiresReview").value(true));
    }

    @Test
    @DisplayName("C3: mismo sector y tipo fuera de la ventana temporal crea un caso nuevo")
    void c3OutsideWindow() throws Exception {
        Instant now = Instant.now();
        JsonNode old = createReport("La Molina", FailureType.CORTE_TOTAL,
                "Corte de energia antiguo", now.minusSeconds(2 * 60 * 60));
        JsonNode recent = createReport("La Molina", FailureType.CORTE_TOTAL,
                "Corte de energia reciente", now.minusSeconds(60));

        assertThat(recent.path("incidentId").asText()).isNotEqualTo(old.path("incidentId").asText());
    }

    @Test
    @DisplayName("C4: sectores distintos no se agrupan aunque coincidan tipo y tiempo")
    void c4DifferentSectors() throws Exception {
        Instant now = Instant.now().minusSeconds(120);
        JsonNode miraflores = createReport("Miraflores", FailureType.CORTE_TOTAL,
                "Sin luz en Miraflores", now);
        JsonNode surco = createReport("Santiago de Surco", FailureType.CORTE_TOTAL,
                "Sin luz en Surco", now.plusSeconds(30));

        assertThat(surco.path("incidentId").asText())
                .isNotEqualTo(miraflores.path("incidentId").asText());
    }

    @Test
    @DisplayName("C5: ubicacion incompleta genera un caso de Baja Certeza")
    void c5MissingSector() throws Exception {
        JsonNode response = createReport(null, FailureType.CORTE_TOTAL,
                "No tengo luz y no se cual es mi sector", Instant.now().minusSeconds(60));

        assertThat(response.path("certainty").asText()).isEqualTo("BAJA");
        assertThat(response.path("requiresReview").asBoolean()).isTrue();
        assertThat(response.path("incidentStatus").asText()).isEqualTo("SUGERIDA");
    }

    @Test
    @DisplayName("Validaciones: reporte al futuro y tipo de falla inexistente")
    void validationErrors() throws Exception {
        ObjectNode future = objectMapper.createObjectNode();
        future.put("sector", "Jesus Maria");
        future.put("failureType", "CORTE_TOTAL");
        future.put("description", "Reporte con fecha futura");
        future.put("reportedAt", Instant.now().plusSeconds(3600).toString());

        mvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(future)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        ObjectNode invalidType = objectMapper.createObjectNode();
        invalidType.put("sector", "Jesus Maria");
        invalidType.put("failureType", "APAGON_NUCLEAR");
        invalidType.put("description", "Tipo no soportado");

        mvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidType)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    @DisplayName("Salud: Actuator responde liveness y readiness para el orquestador")
    void healthEndpoints() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private JsonNode createReport(String sector, FailureType failureType,
                                  String description, Instant reportedAt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        if (sector != null) {
            body.put("sector", sector);
        }
        body.put("failureType", failureType.name());
        body.put("description", description);
        if (reportedAt != null) {
            body.put("reportedAt", reportedAt.toString());
        }

        String response = mvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor-Id", "cliente-demo")
                        .header("X-Actor-Role", "CLIENTE")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return objectMapper.readTree(response);
    }

    private JsonNode getReport(String reportId) throws Exception {
        String response = mvc.perform(get("/api/v1/reports/{id}", reportId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private org.springframework.test.web.servlet.ResultActions changeTechnicianStatus(String incidentId,
                                                                                    String target) throws Exception {
        return mvc.perform(post("/api/v1/technician/incidents/{id}/status", incidentId)
                .header("X-Actor-Id", "tec-1")
                .header("X-Actor-Role", "TECNICO")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + target + "\"}"));
    }

    private MockHttpServletRequestBuilder asOperator(MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-Actor-Id", "op-1")
                .header("X-Actor-Role", "OPERADOR");
    }
}
