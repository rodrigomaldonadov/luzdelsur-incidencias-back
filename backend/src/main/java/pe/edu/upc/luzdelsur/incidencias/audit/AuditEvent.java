package pe.edu.upc.luzdelsur.incidencias.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Evento de auditoria. Nunca guarda contrasenas, tokens ni la descripcion
 * libre del reporte (puede contener datos personales, Ley 29733).
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "actor_role", length = 16)
    private String actorRole;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
        // JPA
    }

    public static AuditEvent of(String actorId, String actorRole, AuditAction action,
                                String entityType, String entityId, String detail, Instant now) {
        AuditEvent event = new AuditEvent();
        event.actorId = truncate(actorId, 64);
        event.actorRole = truncate(actorRole, 16);
        event.action = action.name();
        event.entityType = truncate(entityType, 40);
        event.entityId = truncate(entityId, 64);
        event.detail = truncate(detail, 500);
        event.createdAt = now;
        return event;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
