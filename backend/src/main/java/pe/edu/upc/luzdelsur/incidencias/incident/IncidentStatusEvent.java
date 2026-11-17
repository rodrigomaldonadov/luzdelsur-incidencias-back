package pe.edu.upc.luzdelsur.incidencias.incident;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pe.edu.upc.luzdelsur.incidencias.security.Actor;

/**
 * Ciclo de vida del caso: confirmaciones, cambios de estado y asignaciones.
 * Complementa {@code report_link_history}, que registra los vinculos de cada
 * reporte. Aqui queda, por ejemplo, la nota con la que el tecnico resolvio.
 */
@Entity
@Table(name = "incident_status_history")
public class IncidentStatusEvent {

    public enum EventType {
        CAMBIO_ESTADO,
        ASIGNACION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 24)
    private IncidentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 24)
    private IncidentStatus toStatus;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "actor_role", length = 16)
    private String actorRole;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentStatusEvent() {
        // JPA
    }

    public static IncidentStatusEvent statusChange(UUID incidentId, IncidentStatus from, IncidentStatus to,
                                                   Actor actor, String note, Instant now) {
        return build(incidentId, EventType.CAMBIO_ESTADO, from, to, actor, note, now);
    }

    public static IncidentStatusEvent assignment(UUID incidentId, IncidentStatus current,
                                                 Actor actor, String note, Instant now) {
        return build(incidentId, EventType.ASIGNACION, current, current, actor, note, now);
    }

    private static IncidentStatusEvent build(UUID incidentId, EventType type, IncidentStatus from,
                                             IncidentStatus to, Actor actor, String note, Instant now) {
        IncidentStatusEvent event = new IncidentStatusEvent();
        event.incidentId = incidentId;
        event.eventType = type;
        event.fromStatus = from;
        event.toStatus = to;
        event.actorId = actor == null ? null : actor.id();
        event.actorRole = actor == null || actor.role() == null ? null : actor.role().name();
        event.note = note == null || note.length() <= 500 ? note : note.substring(0, 500);
        event.createdAt = now;
        return event;
    }

    public Long getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public IncidentStatus getFromStatus() {
        return fromStatus;
    }

    public IncidentStatus getToStatus() {
        return toStatus;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
