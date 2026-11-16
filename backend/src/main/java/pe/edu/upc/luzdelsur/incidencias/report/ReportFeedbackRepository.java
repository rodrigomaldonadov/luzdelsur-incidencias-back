package pe.edu.upc.luzdelsur.incidencias.report;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportFeedbackRepository extends JpaRepository<ReportFeedback, Long> {

    Optional<ReportFeedback> findByReportId(UUID reportId);

    List<ReportFeedback> findByReportIdIn(Collection<UUID> reportIds);

    boolean existsByReportId(UUID reportId);
}
