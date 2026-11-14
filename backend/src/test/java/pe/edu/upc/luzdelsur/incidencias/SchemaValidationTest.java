package pe.edu.upc.luzdelsur.incidencias;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import pe.edu.upc.luzdelsur.incidencias.incident.IncidentRepository;
import pe.edu.upc.luzdelsur.incidencias.report.ReportRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que la migracion Flyway y el mapeo JPA coincidan (ddl-auto=validate).
 * Se ejecuta sobre H2 en modo PostgreSQL para no requerir Docker en la maquina
 * del estudiante; la validacion real con PostgreSQL ocurre en el despliegue.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:schema;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class SchemaValidationTest {

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private ReportRepository reports;

    @Test
    void flywaySchemaMatchesEntities() {
        assertThat(incidents.count()).isZero();
        assertThat(reports.count()).isZero();
    }
}
