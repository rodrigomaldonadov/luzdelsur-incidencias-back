package pe.edu.upc.luzdelsur.incidencias.report;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;

public interface ReportRepository extends JpaRepository<Report, UUID>, JpaSpecificationExecutor<Report> {

    List<Report> findByIncidentIdOrderByReportedAtAsc(UUID incidentId);

    Page<Report> findByClientId(String clientId, Pageable pageable);

    long countByIncidentId(UUID incidentId);

    long countByReportedAtAfter(Instant since);

    @Query("""
            select r.description from Report r
            where r.incident.id = :incidentId and r.description is not null
            order by r.reportedAt desc
            """)
    List<String> findRecentDescriptions(@Param("incidentId") UUID incidentId, Pageable pageable);

    @Query("""
            select r.linkCertainty from Report r
            where r.incident.id = :incidentId and r.linkCertainty is not null
            """)
    List<Certainty> findLinkCertainties(@Param("incidentId") UUID incidentId);

    @Query("""
            select count(r) as total,
                   min(r.reportedAt) as firstReportedAt,
                   max(r.reportedAt) as lastReportedAt
            from Report r
            where r.incident.id = :incidentId
            """)
    ReportStats findStats(@Param("incidentId") UUID incidentId);

    List<Report> findByIncidentIdInOrderByReportedAtAsc(Collection<UUID> incidentIds);

    interface ReportStats {

        long getTotal();

        Instant getFirstReportedAt();

        Instant getLastReportedAt();
    }
}
