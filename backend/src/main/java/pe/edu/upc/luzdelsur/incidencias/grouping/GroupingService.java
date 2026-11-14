package pe.edu.upc.luzdelsur.incidencias.grouping;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.upc.luzdelsur.incidencias.config.AppProperties;
import pe.edu.upc.luzdelsur.incidencias.incident.Incident;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentRepository;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.report.FailureType;
import pe.edu.upc.luzdelsur.incidencias.report.ReportRepository;

/**
 * Carga los casos candidatos y delega la decision en {@link GroupingEngine}.
 */
@Service
public class GroupingService {

    private static final Set<IncidentStatus> CANDIDATE_STATUSES = EnumSet.of(
            IncidentStatus.SUGERIDA,
            IncidentStatus.CONFIRMADA,
            IncidentStatus.EN_ATENCION,
            IncidentStatus.REABIERTA);

    private static final int DESCRIPTIONS_PER_CANDIDATE = 4;

    private final IncidentRepository incidents;
    private final ReportRepository reports;
    private final GroupingEngine engine;
    private final AppProperties properties;

    public GroupingService(IncidentRepository incidents, ReportRepository reports,
                           GroupingEngine engine, AppProperties properties) {
        this.incidents = incidents;
        this.reports = reports;
        this.engine = engine;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public GroupingProposal propose(String sectorKey, FailureType failureType,
                                    String description, Instant reportedAt) {
        return engine.propose(sectorKey, failureType, description, reportedAt,
                loadCandidates(sectorKey, failureType, reportedAt));
    }

    private List<GroupingCandidate> loadCandidates(String sectorKey, FailureType failureType, Instant reportedAt) {
        long windowMinutes = properties.getGrouping().getWindowMinutes();
        int limit = Math.max(1, properties.getGrouping().getMaxCandidates());
        Instant since = reportedAt.minus(Duration.ofMinutes(windowMinutes));
        PageRequest limitRequest = PageRequest.of(0, limit);

        List<Incident> candidates = sectorKey == null
                ? incidents.findCandidatesWithoutSector(failureType, CANDIDATE_STATUSES, since, limitRequest)
                : incidents.findCandidatesBySector(sectorKey, CANDIDATE_STATUSES, since, limitRequest);

        return candidates.stream()
                .map(incident -> new GroupingCandidate(
                        incident,
                        reports.findRecentDescriptions(incident.getId(),
                                PageRequest.of(0, DESCRIPTIONS_PER_CANDIDATE))))
                .toList();
    }
}
