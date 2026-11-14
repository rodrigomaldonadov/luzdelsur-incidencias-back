package pe.edu.upc.luzdelsur.incidencias.report;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportLinkHistoryRepository extends JpaRepository<ReportLinkHistory, Long> {

    List<ReportLinkHistory> findByReportIdOrderByCreatedAtAsc(UUID reportId);

    List<ReportLinkHistory> findByReportIdInOrderByCreatedAtAsc(Collection<UUID> reportIds);

    @Query("""
            select h from ReportLinkHistory h
            where h.fromIncidentId = :incidentId or h.toIncidentId = :incidentId
            order by h.createdAt asc
            """)
    List<ReportLinkHistory> findByIncidentId(@Param("incidentId") UUID incidentId);
}
