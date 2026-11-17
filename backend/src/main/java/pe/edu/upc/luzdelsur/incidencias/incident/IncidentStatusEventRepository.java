package pe.edu.upc.luzdelsur.incidencias.incident;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentStatusEventRepository extends JpaRepository<IncidentStatusEvent, Long> {

    List<IncidentStatusEvent> findByIncidentIdOrderByCreatedAtAscIdAsc(UUID incidentId);
}
