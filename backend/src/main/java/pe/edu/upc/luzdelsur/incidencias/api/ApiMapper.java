package pe.edu.upc.luzdelsur.incidencias.api;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.edu.upc.luzdelsur.incidencias.api.dto.OperatorDtos;
import pe.edu.upc.luzdelsur.incidencias.api.dto.ReportDtos;
import pe.edu.upc.luzdelsur.incidencias.api.dto.TechnicianDtos;
import pe.edu.upc.luzdelsur.incidencias.grouping.GroupingProposal;
import pe.edu.upc.luzdelsur.incidencias.incident.Certainty;
import pe.edu.upc.luzdelsur.incidencias.incident.Incident;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatus;
import pe.edu.upc.luzdelsur.incidencias.incident.IncidentStatusEvent;
import pe.edu.upc.luzdelsur.incidencias.report.Report;
import pe.edu.upc.luzdelsur.incidencias.report.ReportFeedback;
import pe.edu.upc.luzdelsur.incidencias.report.ReportLinkHistory;

/**
 * Traduce entidades a DTOs y centraliza los mensajes dirigidos al cliente.
 * El cliente nunca recibe identificadores internos de operadores ni notas
 * privadas: solo el estado comprensible de su reporte.
 */
@Component
public class ApiMapper {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public ReportDtos.ReportView reportView(Report report) {
        return new ReportDtos.ReportView(
                report.getId(),
                report.getSector(),
                report.getReference(),
                report.getFailureType(),
                report.getDescription(),
                report.getReportedAt(),
                report.getIncident() == null ? null : report.getIncident().getId(),
                report.getLinkStatus(),
                report.getLinkCertainty(),
                report.getGroupingReason(),
                report.getSimilarityScore(),
                report.getLinkUpdatedAt());
    }

    public OperatorDtos.IncidentSummary summary(Incident incident) {
        return new OperatorDtos.IncidentSummary(
                incident.getId(),
                incident.getSector(),
                incident.getFailureType(),
                incident.getStatus(),
                incident.getCertainty(),
                incident.isRequiresReview(),
                incident.getPriorityScore(),
                incident.getPriorityLabel(),
                incident.getReportCount(),
                incident.getFirstReportedAt(),
                incident.getLastReportedAt(),
                incident.getConfirmedAt(),
                incident.getConfirmedBy(),
                incident.getAssignedTechnicianId(),
                incident.getAssignedAt(),
                incident.getClosedAt(),
                incident.getUpdatedAt(),
                incident.getVersion());
    }

    public OperatorDtos.IncidentDetail detail(Incident incident, List<Report> reports,
                                              List<ReportLinkHistory> history,
                                              List<IncidentStatusEvent> statusEvents,
                                              Map<String, String> actorNames) {
        return new OperatorDtos.IncidentDetail(
                summary(incident),
                reports.stream().map(this::reportView).toList(),
                history.stream().map(entry -> historyView(entry, actorNames)).toList(),
                alerts(incident, reports),
                statusEvents.stream().map(event -> statusEventView(event, actorNames)).toList());
    }

    public OperatorDtos.LinkHistoryView historyView(ReportLinkHistory history, Map<String, String> actorNames) {
        return new OperatorDtos.LinkHistoryView(
                history.getId(),
                history.getReportId(),
                history.getFromIncidentId(),
                history.getToIncidentId(),
                history.getAction(),
                history.getActorId(),
                history.getNote(),
                history.getCreatedAt(),
                actorNames.get(history.getActorId()));
    }

    public OperatorDtos.StatusEventView statusEventView(IncidentStatusEvent event, Map<String, String> actorNames) {
        return new OperatorDtos.StatusEventView(
                event.getId(),
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getActorId(),
                actorNames.get(event.getActorId()),
                event.getActorRole(),
                event.getNote(),
                event.getCreatedAt());
    }

    public ReportDtos.CreateResponse createResponse(Report report, Incident incident, GroupingProposal proposal) {
        return new ReportDtos.CreateResponse(
                reportView(report),
                incident.getId(),
                incident.getStatus(),
                proposal.certainty(),
                proposal.requiresReview(),
                clientMessageForCreate(proposal),
                proposal.reasons());
    }

    public ReportDtos.TrackingResponse tracking(Report report, Incident incident, String clientMessage) {
        return tracking(report, incident, clientMessage, null);
    }

    public ReportDtos.TrackingResponse tracking(Report report, Incident incident, String clientMessage,
                                                ReportFeedback feedback) {
        UUID incidentId = incident == null ? null : incident.getId();
        IncidentStatus status = incident == null ? null : incident.getStatus();
        Certainty certainty = incident == null ? null : incident.getCertainty();
        boolean requiresReview = incident != null && incident.isRequiresReview();
        Instant updatedAt = incident == null ? null : incident.getUpdatedAt();

        return new ReportDtos.TrackingResponse(
                reportView(report), incidentId, status, certainty, requiresReview, updatedAt, clientMessage,
                feedback == null && (status == IncidentStatus.RESUELTA || status == IncidentStatus.CERRADA),
                feedback == null ? null : new ReportDtos.FeedbackView(
                        feedback.getRating(), feedback.getComment(), feedback.getCreatedAt()));
    }

    public TechnicianDtos.TechnicianIncidentView technicianView(Incident incident, List<Report> reports) {
        List<TechnicianDtos.TechnicianReportView> reportViews = reports.stream()
                .map(report -> new TechnicianDtos.TechnicianReportView(
                        report.getId(),
                        report.getSector(),
                        report.getReference(),
                        report.getFailureType(),
                        report.getDescription(),
                        report.getReportedAt(),
                        report.getLinkStatus(),
                        report.getLinkCertainty(),
                        report.getLatitude(),
                        report.getLongitude()))
                .toList();

        return new TechnicianDtos.TechnicianIncidentView(
                incident.getId(),
                incident.getSector(),
                incident.getFailureType(),
                incident.getStatus(),
                incident.getCertainty(),
                incident.isRequiresReview(),
                incident.getPriorityLabel(),
                incident.getPriorityScore(),
                incident.getReportCount(),
                incident.getFirstReportedAt(),
                incident.getLastReportedAt(),
                incident.getConfirmedAt(),
                incident.getConfirmedBy(),
                incident.getStatusUpdatedAt(),
                incident.getStatusUpdatedBy(),
                incident.getAssignedTechnicianId(),
                incident.getAssignedAt(),
                incident.getClosedAt(),
                incident.getUpdatedAt(),
                estimatedScope(incident),
                reportViews,
                alerts(incident, reports));
    }

    public TechnicianDtos.TechnicianIncidentSummary technicianSummary(Incident incident) {
        return new TechnicianDtos.TechnicianIncidentSummary(
                incident.getId(),
                incident.getSector(),
                incident.getFailureType(),
                incident.getStatus(),
                incident.getCertainty(),
                incident.isRequiresReview(),
                incident.getPriorityLabel(),
                incident.getPriorityScore(),
                incident.getReportCount(),
                incident.getFirstReportedAt(),
                incident.getLastReportedAt(),
                incident.getAssignedTechnicianId(),
                incident.getAssignedAt(),
                estimatedScope(incident),
                incident.getStatusUpdatedAt(),
                incident.getClosedAt(),
                incident.getUpdatedAt());
    }

    public String clientMessageForCreate(GroupingProposal proposal) {
        if (!proposal.aggregates()) {
            if (proposal.certainty() == Certainty.BAJA) {
                return "Registramos tu reporte. No pudimos determinar tu sector, por lo que un operador lo revisara.";
            }
            return "Registramos tu reporte. Aun no hay reportes cercanos del mismo tipo; un operador revisara el caso.";
        }
        return switch (proposal.certainty()) {
            case ALTA -> "Tu reporte se vinculo a una incidencia existente en tu sector. Un operador confirmara la agrupacion.";
            case MEDIA -> "Tu reporte se vinculo a una incidencia existente en tu sector; la coincidencia esta en revision.";
            case BAJA -> "Tu reporte quedo registrado y en revision: la coincidencia con otros reportes no es concluyente.";
        };
    }

    public String clientMessageForStatus(Incident incident) {
        if (incident == null) {
            return "Tu reporte fue separado del caso inicial y sera revisado nuevamente por un operador.";
        }
        if (incident.getStatus() == null) {
            return "Tu reporte esta registrado.";
        }
        return switch (incident.getStatus()) {
            case SUGERIDA -> incident.getCertainty() == Certainty.BAJA
                    ? "Tu reporte esta vinculado a un caso en revision. Un operador debe confirmar la agrupacion."
                    : "Tu reporte esta vinculado a una posible incidencia en tu sector, pendiente de confirmacion.";
            case CONFIRMADA -> "Tu reporte esta vinculado a una incidencia confirmada en tu sector.";
            case EN_ATENCION -> "Se esta atendiendo la incidencia vinculada a tu reporte.";
            case RESUELTA -> "La incidencia fue reportada como resuelta. Si la falla persiste, puedes avisarlo.";
            case CERRADA -> "El caso fue cerrado. Si la falla persiste, puedes avisarlo.";
            case REABIERTA -> "Registramos que la falla persiste. El caso fue reabierto para revision.";
            case DESCARTADA -> "Tu reporte fue separado del caso inicial y sera revisado nuevamente por un operador.";
        };
    }

    public List<String> alerts(Incident incident, List<Report> reports) {
        List<String> alerts = new ArrayList<>();

        if (incident.getCertainty() == Certainty.BAJA) {
            alerts.add("Baja certeza: el operador debe revisar la agrupacion antes de consolidarla.");
        } else if (incident.getCertainty() == Certainty.MEDIA) {
            alerts.add("Certeza media: la coincidencia no cuenta con senal semantica suficiente.");
        }

        long missingSector = reports.stream()
                .filter(report -> report.getSectorKey() == null
                        || Incident.UNKNOWN_SECTOR_KEY.equals(report.getSectorKey()))
                .count();
        if (missingSector > 0) {
            alerts.add(missingSector + " reporte(s) con ubicacion incompleta o sector ambiguo.");
        }

        long distinctTypes = reports.stream()
                .map(Report::getFailureType)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        if (distinctTypes > 1) {
            alerts.add("Los reportes vinculados tienen tipos de falla distintos.");
        }

        if (incident.getStatus() == IncidentStatus.REABIERTA) {
            alerts.add("El cliente indico que la falla persiste: el caso fue reabierto.");
        }
        if (incident.getStatus() == IncidentStatus.SUGERIDA) {
            alerts.add("Agrupacion pendiente de confirmacion por el operador.");
        }
        if (incident.getStatus() == IncidentStatus.DESCARTADA) {
            alerts.add("Caso descartado: no quedan reportes vigentes vinculados.");
        }
        return alerts;
    }

    private String estimatedScope(Incident incident) {
        if (incident.getReportCount() == 0) {
            return "Sin reportes vigentes";
        }
        return "Sector " + incident.getSector()
                + " - " + incident.getReportCount() + " reporte(s) entre "
                + DATE_TIME.format(incident.getFirstReportedAt().atZone(LIMA))
                + " y " + DATE_TIME.format(incident.getLastReportedAt().atZone(LIMA));
    }
}
