package dev.sellerkit.reconkit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A row whose only job is to be locked.
 *
 * <p>Generating a settlement is not safe to do twice concurrently, and the guard has to
 * hold across processes. An in process lock protects one JVM and quietly stops
 * protecting anything the moment a second instance is started, which is exactly when
 * the traffic that needs the protection shows up. A row locked with SELECT FOR UPDATE
 * is held by the database, so every instance queues behind the same thing.
 */
@Entity
@Table(name = "settlement_lock")
public class SettlementLock {

    /** tenant:counterparty:periodStart:periodEnd */
    @Id
    @Column(name = "lock_key", length = 160)
    private String lockKey;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt = Instant.now();

    protected SettlementLock() {
    }

    public SettlementLock(String lockKey) {
        this.lockKey = lockKey;
    }

    public String getLockKey() {
        return lockKey;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }
}
