package pe.edu.upc.luzdelsur.incidencias.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import pe.edu.upc.luzdelsur.incidencias.api.dto.ReportDtos;
import pe.edu.upc.luzdelsur.incidencias.auth.AppUser;
import pe.edu.upc.luzdelsur.incidencias.auth.AppUserRepository;
import pe.edu.upc.luzdelsur.incidencias.auth.AuthService;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentService;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;
import pe.edu.upc.luzdelsur.incidencias.report.ReportRepository;
import pe.edu.upc.luzdelsur.incidencias.report.ReportService;
import pe.edu.upc.luzdelsur.incidencias.security.Actor;
import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

/**
 * Datos simulados para la demo (prototipo academico, sin datos reales de clientes).
 *
 * <ul>
 *   <li>Cuentas: operador, dos tecnicos y un cliente, con la contrasena de
 *       {@code APP_DEMO_PASSWORD}. Solo se crean si no existen.</li>
 *   <li>Reportes: dataset que reproduce C1-C5 y casos ya confirmados, en
 *       atencion y resueltos. Solo se carga si la tabla de reportes esta vacia.
 *       Pasa por el mismo motor y servicios que la API: no inserta SQL directo.</li>
 * </ul>
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String OPERATOR_USER = "operador@luzdelsur.com.pe";
    static final String TECHNICIAN_USER = "tecnico@luzdelsur.com.pe";
    static final String TECHNICIAN_2_USER = "tecnico2@luzdelsur.com.pe";
    static final String CLIENT_USER = "cliente@luzdelsur.com.pe";

    private final AppProperties properties;
    private final AppUserRepository users;
    private final AuthService authService;
    private final ReportRepository reports;
    private final ReportService reportService;
    private final IncidentService incidentService;
    private final Clock clock;

    public DemoDataSeeder(AppProperties properties, AppUserRepository users, AuthService authService,
                          ReportRepository reports, ReportService reportService,
                          IncidentService incidentService, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.authService = authService;
        this.reports = reports;
        this.reportService = reportService;
        this.incidentService = incidentService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Demo demo = properties.getDemo();
        if (!demo.isSeedUsers()) {
            return;
        }
        if (demo.getPassword() == null || demo.getPassword().length() < 6) {
            log.warn("APP_DEMO_PASSWORD vacio o menor a 6 caracteres: no se crean cuentas de demo");
            return;
        }
        try {
            AppUser operator = ensureUser("Mesa de Control Central", OPERATOR_USER, ActorRole.OPERADOR);
            AppUser technician = ensureUser("Cuadrilla T-08 (C. Huaman)", TECHNICIAN_USER, ActorRole.TECNICO);
            ensureUser("Cuadrilla T-12 (R. Quispe)", TECHNICIAN_2_USER, ActorRole.TECNICO);
            AppUser client = ensureUser("Cliente Demo", CLIENT_USER, ActorRole.CLIENTE);
            log.info("Cuentas de demo listas: {}, {}, {}, {}", OPERATOR_USER, TECHNICIAN_USER,
                    TECHNICIAN_2_USER, CLIENT_USER);

            if (demo.isSeedReports() && reports.count() == 0) {
                seedReports(operator, technician, client);
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo cargar la semilla de demo: {}", ex.getMessage());
        }
    }

    private AppUser ensureUser(String name, String username, ActorRole role) {
        return users.findByUsername(username)
                .orElseGet(() -> authService.createUser(name, username, properties.getDemo().getPassword(), role));
    }

    private void seedReports(AppUser operatorUser, AppUser technicianUser, AppUser clientUser) {
        Instant now = clock.instant();
        String client = clientUser.getId().toString();
        Actor operator = new Actor(operatorUser.getId().toString(), ActorRole.OPERADOR);
        Actor technician = new Actor(technicianUser.getId().toString(), ActorRole.TECNICO);

        // Caso resuelto (historial del tecnico; el cliente puede calificar o reportar persistencia).
        UUID sanBorja = report(client, "San Borja", "Av. Guardia Civil 640", FailureType.BAJA_TENSION,
                "Los focos estan muy bajos y la refrigeradora se apaga sola", -12.0910, -77.0010, now, 200).incidentId();
        report(null, "San Borja", "Av. Guardia Civil 658", FailureType.BAJA_TENSION,
                "Baja tension en toda la cuadra, los equipos no encienden", -12.0915, -77.0005, now, 195);
        incidentService.confirm(sanBorja, "Agrupacion verificada por el operador de turno", operator);
        incidentService.assignTechnician(sanBorja, technician.id(), "Cuadrilla mas cercana", operator);
        incidentService.changeStatus(sanBorja, IncidentStatus.EN_ATENCION, "Cuadrilla en camino", technician);
        incidentService.changeStatus(sanBorja, IncidentStatus.RESUELTA,
                "Reemplazo de fusible de 20 A en el poste de distribucion", technician);

        // Caso en atencion.
        UUID chorrillos = report(null, "Chorrillos", "Av. Huaylas 1200", FailureType.ALUMBRADO_PUBLICO,
                "Los postes de la avenida estan apagados toda la noche", -12.1690, -77.0210, now, 70).incidentId();
        incidentService.confirm(chorrillos, "Alumbrado publico confirmado", operator);
        incidentService.changeStatus(chorrillos, IncidentStatus.EN_ATENCION, "Inicio de inspeccion", technician);

        // Caso confirmado y asignado, pendiente de atencion.
        UUID lince = report(null, "Lince", "Jr. Mariano Carranza 450", FailureType.CORTE_TOTAL,
                "Se fue la luz en toda la manzana despues de un chispazo", -12.0850, -77.0350, now, 90).incidentId();
        report(null, "Lince", "Av. Arequipa 2100", FailureType.CORTE_TOTAL,
                "Corte de energia en el edificio y en los vecinos", -12.0860, -77.0340, now, 85);
        incidentService.confirm(lince, "Dos reportes coherentes en el mismo sector", operator);
        incidentService.assignTechnician(lince, technician.id(), null, operator);

        // C1: tres reportes mismo sector, tipo y ventana (pendiente de confirmar).
        report(client, "San Isidro", "Av. Arequipa 1234", FailureType.CORTE_TOTAL,
                "No hay luz en toda la cuadra desde hace 20 minutos", -12.0975, -77.0365, now, 25);
        report(null, "San Isidro", "Calle Las Camelias 280", FailureType.CORTE_TOTAL,
                "Corte de energia en mi casa y en la de los vecinos", -12.0980, -77.0360, now, 20);
        report(null, "san isidro", "Av. Petit Thouars 3400", FailureType.CORTE_TOTAL,
                "Se fue la electricidad en la zona", null, null, now, 12);

        // C2: mismo sector y ventana con distinto tipo de falla (Baja certeza).
        report(client, "Barranco", "Av. Grau 300", FailureType.CORTE_TOTAL,
                "Corte total de energia en la calle", null, null, now, 18);
        report(null, "Barranco", "Jr. Union 120", FailureType.BAJA_TENSION,
                "La tension baja y los focos parpadean", null, null, now, 15);

        // C3: mismo sector y tipo fuera de la ventana (casos distintos).
        report(null, "Miraflores", "Calle Schell 200", FailureType.INTERMITENCIA,
                "La luz se corta cada diez minutos", null, null, now, 150);
        report(null, "Miraflores", "Av. Larco 500", FailureType.INTERMITENCIA,
                "Microcortes continuos en el departamento", null, null, now, 40);

        // C4: sectores distintos con mismo tipo y hora.
        report(null, "Santiago de Surco", "Av. Primavera 900", FailureType.CORTE_TOTAL,
                "Apagon en la urbanizacion", null, null, now, 10);
        report(null, "Surquillo", "Av. Angamos Este 1840", FailureType.CORTE_TOTAL,
                "Sin energia en los comercios de la avenida", null, null, now, 10);

        // C5: sin sector informado (Baja certeza).
        report(client, null, "Cerca al parque", FailureType.MEDIDOR,
                "El medidor hace ruido y huele a quemado", null, null, now, 8);

        log.info("Dataset de demo cargado ({} reportes)", reports.count());
    }

    private ReportDtos.CreateResponse report(String clientId, String sector, String reference, FailureType type,
                                             String description, Double latitude, Double longitude,
                                             Instant now, long minutesAgo) {
        return reportService.create(new ReportDtos.CreateRequest(
                clientId, sector, reference, type, description, latitude, longitude,
                now.minus(Duration.ofMinutes(minutesAgo))));
    }
}
