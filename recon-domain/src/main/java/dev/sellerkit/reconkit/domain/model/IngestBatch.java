package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.EntrySide;
import dev.sellerkit.reconkit.domain.enums.IngestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One load of data: an uploaded file or one API pull.
 *
 * <p>The content hash is the reason this table exists. Operations teams re-send the
 * same file, and a settlement file loaded twice produces a day of phantom duplicates
 * that look exactly like a real counterparty error. The hash makes the second load a
 * no-op instead of an incident.
 */
@Entity
@Table(name = "ingest_batch")
public class IngestBatch extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 12)
    private EntrySide side;

    @Column(name = "source_name", nullable = false, length = 200)
    private String sourceName;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IngestStatus status = IngestStatus.RECEIVED;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "loaded_at")
    private Instant loadedAt;

    protected IngestBatch() {
    }

    public IngestBatch(Long counterpartyId, EntrySide side, String sourceName,
                       String contentHash, LocalDate businessDate) {
        this.counterpartyId = counterpartyId;
        this.side = side;
        this.sourceName = sourceName;
        this.contentHash = contentHash;
        this.businessDate = businessDate;
    }

    public void markLoaded(int rowCount, int rejectedCount) {
        this.rowCount = rowCount;
        this.rejectedCount = rejectedCount;
        this.status = IngestStatus.LOADED;
        this.loadedAt = Instant.now();
    }

    public void markRejected(String reason) {
        this.status = IngestStatus.REJECTED;
        this.rejectReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getCounterpartyId() {
        return counterpartyId;
    }

    public EntrySide getSide() {
        return side;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getContentHash() {
        return contentHash;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public IngestStatus getStatus() {
        return status;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }
}
