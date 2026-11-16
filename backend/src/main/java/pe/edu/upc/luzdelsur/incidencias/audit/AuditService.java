package pe.edu.upc.luzdelsur.incidencias.audit;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.upc.luzdelsur.incidencias.common.PageResponse;
import pe.edu.upc.luzdelsur.incidencias.security.Actor;

/**
 * Auditoria transversal. Se registra en una transaccion propia para que un
 * intento fallido (por ejemplo, un login rechazado) tambien quede asentado, y
 * para que un error de auditoria nunca revierta la operacion de negocio.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Actor actor, AuditAction action, String entityType, Object entityId, String detail) {
        record(actor == null ? null : actor.id(),
                actor == null || actor.role() == null ? null : actor.role().name(),
                action, entityType, entityId, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorId, String actorRole, AuditAction action,
                       String entityType, Object entityId, String detail) {
        try {
            events.save(AuditEvent.of(actorId, actorRole, action, entityType,
                    entityId == null ? null : entityId.toString(), detail, clock.instant()));
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar el evento de auditoria {}: {}", action, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventView> search(String actorId, AuditAction action, String entityId,
                                               Instant from, Instant to, Pageable pageable) {
        Specification<AuditEvent> spec = (root, query, cb) -> cb.conjunction();
        if (actorId != null && !actorId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId.trim()));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action.name()));
        }
        if (entityId != null && !entityId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId.trim()));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        Page<AuditEvent> page = events.findAll(spec, pageable);
        return PageResponse.from(page, AuditEventView::from);
    }

    public record AuditEventView(
            Long id,
            String actorId,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            String detail,
            Instant createdAt) {

        static AuditEventView from(AuditEvent event) {
            return new AuditEventView(event.getId(), event.getActorId(), event.getActorRole(),
                    event.getAction(), event.getEntityType(), event.getEntityId(),
                    event.getDetail(), event.getCreatedAt());
        }
    }
}
