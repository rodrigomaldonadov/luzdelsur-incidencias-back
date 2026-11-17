package pe.edu.upc.luzdelsur.incidencias.config;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import pe.edu.upc.luzdelsur.incidencias.auth.AppUserRepository;
import pe.edu.upc.luzdelsur.incidencias.incident.Incident;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentRepository;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.report.ReportRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La semilla corre sobre las migraciones Flyway reales y por los mismos
 * servicios que la API. Si algo falla, el seeder solo registra un aviso, por
 * eso aqui se verifica el resultado y no solo que el contexto arranque.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:seed;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.demo.seed-users=true",
        "app.demo.seed-reports=true",
        "app.demo.password=12345678"
})
@ActiveProfiles("test")
class DemoDataSeederTest {

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ReportRepository reports;

    @Autowired
    private IncidentRepository incidents;

    @Test
    void seedsAccountsAndDataset() {
        assertThat(users.findByUsername(DemoDataSeeder.OPERATOR_USER)).isPresent();
        assertThat(users.findByUsername(DemoDataSeeder.TECHNICIAN_USER)).isPresent();
        assertThat(users.findByUsername(DemoDataSeeder.TECHNICIAN_2_USER)).isPresent();
        assertThat(users.findByUsername(DemoDataSeeder.CLIENT_USER)).isPresent();

        assertThat(reports.count()).isEqualTo(15);

        Map<IncidentStatus, Long> byStatus = incidents.findAll().stream()
                .collect(Collectors.groupingBy(Incident::getStatus, Collectors.counting()));
        assertThat(byStatus.get(IncidentStatus.RESUELTA)).isEqualTo(1);
        assertThat(byStatus.get(IncidentStatus.EN_ATENCION)).isEqualTo(1);
        assertThat(byStatus.get(IncidentStatus.CONFIRMADA)).isEqualTo(1);
        assertThat(byStatus.get(IncidentStatus.SUGERIDA)).isGreaterThanOrEqualTo(6);

        String clientId = users.findByUsername(DemoDataSeeder.CLIENT_USER).orElseThrow().getId().toString();
        assertThat(reports.findAll().stream().filter(r -> clientId.equals(r.getClientId())).count()).isEqualTo(4);

        Map<String, Incident> bySector = incidents.findAll().stream()
                .filter(incident -> incident.getStatus() != IncidentStatus.SUGERIDA)
                .collect(Collectors.toMap(Incident::getSector, Function.identity(), (a, b) -> a));
        assertThat(bySector.get("Lince").getAssignedTechnicianId()).isNotNull();
    }
}
