package pe.edu.upc.luzdelsur.incidencias.incident;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.upc.luzdelsur.incidencias.report.FailureType;

public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    @Query("""
            select i from Incident i
            where i.sectorKey = :sectorKey
              and i.status in :statuses
              and i.lastReportedAt >= :since
            order by i.lastReportedAt desc
            """)
    List<Incident> findCandidatesBySector(@Param("sectorKey") String sectorKey,
                                          @Param("statuses") Collection<IncidentStatus> statuses,
                                          @Param("since") Instant since,
                                          Pageable pageable);

    @Query("""
            select i from Incident i
            where i.status in :statuses
              and i.failureType = :failureType
              and i.lastReportedAt >= :since
            order by i.lastReportedAt desc
            """)
    List<Incident> findCandidatesWithoutSector(@Param("failureType") FailureType failureType,
                                               @Param("statuses") Collection<IncidentStatus> statuses,
                                               @Param("since") Instant since,
                                               Pageable pageable);
}
