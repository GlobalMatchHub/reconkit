package dev.sellerkit.reconkit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append only. Nothing in the application updates or deletes a row here.
 *
 * <p>The before and after images are stored as JSON rather than as a rendered message,
 * because the question asked six months later is never the one the message was written
 * to answer.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor", nullable = false, length = 160)
    private String actor;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 60)
    private String entityId;

    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    /** Ties every row written during one HTTP call together. */
    @Column(name = "request_id", nullable = false, length = 40)
    private String requestId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected AuditLog() {
    }

    public AuditLog(String actor, String action, String entityType, String entityId,
                    String beforeJson, String afterJson, String requestId) {
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.requestId = requestId;
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
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

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
