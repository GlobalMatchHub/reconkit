package dev.sellerkit.reconkit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;

/**
 * An organisation using the system. Not tenant scoped itself, for the obvious reason.
 */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * The zone every business date in this tenant is computed in. Reconciliation is a
     * date sensitive operation and the date is not UTC just because the server is.
     */
    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone = "Asia/Seoul";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Tenant() {
    }

    public Tenant(String id, String name, String timeZone) {
        this.id = id;
        this.name = name;
        this.timeZone = timeZone;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
