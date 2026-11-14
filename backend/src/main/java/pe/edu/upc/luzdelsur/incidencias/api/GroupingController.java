package pe.edu.upc.luzdelsur.incidencias.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.upc.luzdelsur.incidencias.api.dto.GroupingDtos;
import pe.edu.upc.luzdelsur.incidencias.common.TextNormalizer;
import pe.edu.upc.luzdelsur.incidencias.config.AppProperties;
import pe.edu.upc.luzdelsur.incidencias.embeddings.DescriptionSimilarityPort;
import pe.edu.upc.luzdelsur.incidencias.grouping.GroupingProposal;
import pe.edu.upc.luzdelsur.incidencias.grouping.GroupingService;
import pe.edu.upc.luzdelsur.incidencias.security.AccessControl;
import pe.edu.upc.luzdelsur.incidencias.security.ActorRole;

@RestController
@RequestMapping("/api/v1/grouping")
@Tag(name = "Motor de agrupacion", description = "Evaluacion de reglas y configuracion vigente")
public class GroupingController {

    private final GroupingService groupingService;
    private final DescriptionSimilarityPort similarityPort;
    private final AppProperties properties;
    private final AccessControl accessControl;
    private final ApiMapper mapper;

    public GroupingController(GroupingService groupingService,
                              DescriptionSimilarityPort similarityPort,
                              AppProperties properties,
                              AccessControl accessControl,
                              ApiMapper mapper) {
        this.groupingService = groupingService;
        this.similarityPort = similarityPort;
        this.properties = properties;
        this.accessControl = accessControl;
        this.mapper = mapper;
    }

    @GetMapping("/config")
    @Operation(summary = "Ventana temporal y umbrales vigentes (utiles para QA y el frontend)")
    public GroupingDtos.GroupingConfigResponse config() {
        AppProperties.Embeddings embeddings = properties.getEmbeddings();
        return new GroupingDtos.GroupingConfigResponse(
                properties.getGrouping().getWindowMinutes(),
                properties.getGrouping().getMaxCandidates(),
                embeddings.isEnabled(),
                similarityPort.isAvailable(),
                similarityPort.status(),
                embeddings.getMatchThreshold(),
                embeddings.getConflictThreshold());
    }

    @PostMapping("/preview")
    @Operation(summary = "Evalua un reporte sin persistirlo (pruebas del motor, H3)")
    public GroupingDtos.PreviewResponse preview(@Valid @RequestBody GroupingDtos.PreviewRequest request) {
        accessControl.require(ActorRole.OPERADOR);

        String sectorKey = TextNormalizer.isAmbiguousSector(request.sector())
                ? null
                : TextNormalizer.normalizeKey(request.sector());
        Instant reportedAt = request.reportedAt() == null ? Instant.now() : request.reportedAt();

        GroupingProposal proposal = groupingService.propose(
                sectorKey, request.failureType(), request.description().trim(), reportedAt);

        return new GroupingDtos.PreviewResponse(
                proposal.aggregates(),
                proposal.candidateIncidentId(),
                proposal.certainty(),
                proposal.requiresReview(),
                proposal.sectorMatch(),
                proposal.timeMatch(),
                proposal.typeMatch(),
                proposal.similarity(),
                proposal.reasons(),
                mapper.clientMessageForCreate(proposal));
    }
}
