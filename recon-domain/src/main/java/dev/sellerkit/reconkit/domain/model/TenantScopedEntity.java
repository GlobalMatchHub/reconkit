package dev.sellerkit.reconkit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import org.hibernate.annotations.TenantId;

/**
 * Every row in this system belongs to exactly one tenant.
 *
 * <p>The tenant column is not a field the application code remembers to filter on.
 * {@code @TenantId} makes Hibernate append the predicate to every select and set the
 * value on every insert, so a repository method written without a tenant argument
 * still cannot read another organisation's rows. Bolting isolation on later means
 * auditing every query ever written; declaring it here means there is nothing to audit.
 */
@MappedSuperclass
public abstract class TenantScopedEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 40)
    private String tenantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getTenantId() {
        return tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
