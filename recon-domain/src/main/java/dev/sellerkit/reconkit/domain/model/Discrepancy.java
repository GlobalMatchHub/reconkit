package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.DiscrepancyStatus;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyType;
import dev.sellerkit.reconkit.domain.enums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A difference the engine could not explain away.
 *
 * <p>Carries an optimistic lock. Two operators working the same morning queue will
 * open the same row, and without a version column the second save silently discards
 * the first one's resolution note. The failure is invisible, which is what makes it
 * worth a column.
 */
@Entity
@Table(name = "discrepancy")
public class Discrepancy extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "group_id")
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private DiscrepancyType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 12)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DiscrepancyStatus status = DiscrepancyStatus.OPEN;

    /** Ledger minus statement. Positive means we booked more than they reported. */
    @Column(name = "delta_minor", nullable = false)
    private long deltaMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "ledger_entry_id")
    private Long ledgerEntryId;

    @Column(name = "statement_entry_id")
    private Long statementEntryId;

    @Column(name = "detail", nullable = false, length = 500)
    private String detail;

    @Column(name = "assignee", length = 160)
    private String assignee;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "resolved_by", length = 160)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * Set when a later run explained the difference by itself, typically a late posting
     * that arrived the next day. Auto closing is recorded, never silent.
     */
    @Column(name = "auto_closed_by_run_id")
    private Long autoClosedByRunId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Discrepancy() {
    }

    public Discrepancy(Long runId, Long counterpartyId, LocalDate businessDate,
                       DiscrepancyType type, Severity severity, long deltaMinorUnits,
                       String currency, String detail) {
        this.runId = runId;
        this.counterpartyId = counterpartyId;
        this.businessDate = businessDate;
        this.type = type;
        this.severity = severity;
        this.deltaMinorUnits = deltaMinorUnits;
        this.currency = currency;
        this.detail = detail;
    }

    public void resolve(DiscrepancyStatus outcome, String note, String actor) {
        if (outcome == DiscrepancyStatus.OPEN || outcome == DiscrepancyStatus.INVESTIGATING) {
            this.status = outcome;
            this.assignee = actor;
            return;
        }
        this.status = outcome;
        this.resolutionNote = note;
        this.resolvedBy = actor;
        this.resolvedAt = Instant.now();
    }

    public void autoClose(Long byRunId, String note) {
        this.status = DiscrepancyStatus.ACCEPTED;
        this.autoClosedByRunId = byRunId;
        this.resolutionNote = note;
        this.resolvedBy = "system";
        this.resolvedAt = Instant.now();
    }

    public boolean isOpen() {
        return status == DiscrepancyStatus.OPEN || status == DiscrepancyStatus.INVESTIGATING;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getCounterpartyId() {
        return counterpartyId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long v) {
        this.groupId = v;
    }

    public DiscrepancyType getType() {
        return type;
    }

    public Severity getSeverity() {
        return severity;
    }

    public DiscrepancyStatus getStatus() {
        return status;
    }

    public long getDeltaMinorUnits() {
        return deltaMinorUnits;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getLedgerEntryId() {
        return ledgerEntryId;
    }

    public void setLedgerEntryId(Long v) {
        this.ledgerEntryId = v;
    }

    public Long getStatementEntryId() {
        return statementEntryId;
    }

    public void setStatementEntryId(Long v) {
        this.statementEntryId = v;
    }

    public String getDetail() {
        return detail;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Long getAutoClosedByRunId() {
        return autoClosedByRunId;
    }

    public long getVersion() {
        return version;
    }
}
