package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.ReconRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One execution of the engine for one counterparty and one business date.
 *
 * <p>A run is immutable once finished. Re-running a date does not edit the previous
 * result, it creates the next sequence number, so last night's numbers stay exactly as
 * they were reported. The unique constraint on (counterparty, date, sequence) is also
 * what stops two schedulers from producing two live runs for the same night: the loser
 * gets a constraint violation and returns the winner's run instead of starting a second one.
 */
@Entity
@Table(name = "recon_run",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recon_run_slot",
                columnNames = {"tenant_id", "counterparty_id", "business_date", "sequence_no"}))
public class ReconRun extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReconRunStatus status = ReconRunStatus.PENDING;

    /** Version of the rule set that produced this run, so old results stay explainable. */
    @Column(name = "rule_version", nullable = false, length = 20)
    private String ruleVersion;

    @Column(name = "ledger_count", nullable = false)
    private int ledgerCount;

    @Column(name = "statement_count", nullable = false)
    private int statementCount;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "discrepancy_count", nullable = false)
    private int discrepancyCount;

    @Column(name = "matched_by_pass_a", nullable = false)
    private int matchedByPassA;

    @Column(name = "matched_by_pass_b", nullable = false)
    private int matchedByPassB;

    @Column(name = "matched_by_pass_c", nullable = false)
    private int matchedByPassC;

    @Column(name = "matched_by_pass_d", nullable = false)
    private int matchedByPassD;

    @Column(name = "ledger_gross_minor", nullable = false)
    private long ledgerGrossMinorUnits;

    @Column(name = "statement_gross_minor", nullable = false)
    private long statementGrossMinorUnits;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMillis;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    protected ReconRun() {
    }

    public ReconRun(Long counterpartyId, LocalDate businessDate, int sequenceNo, String ruleVersion) {
        this.counterpartyId = counterpartyId;
        this.businessDate = businessDate;
        this.sequenceNo = sequenceNo;
        this.ruleVersion = ruleVersion;
    }

    public void start() {
        this.status = ReconRunStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void complete() {
        this.status = ReconRunStatus.COMPLETED;
        this.finishedAt = Instant.now();
        this.durationMillis = startedAt == null ? null : finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    public void fail(String reason) {
        this.status = ReconRunStatus.FAILED;
        this.failureReason = reason;
        this.finishedAt = Instant.now();
        this.durationMillis = startedAt == null ? null : finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    /** Share of rows, both sides counted, that ended up inside a match group. */
    public double matchRate() {
        int total = ledgerCount + statementCount;
        return total == 0 ? 1.0d : (double) matchedCount / (double) total;
    }

    public Long getId() {
        return id;
    }

    public Long getCounterpartyId() {
        return counterpartyId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public ReconRunStatus getStatus() {
        return status;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public int getLedgerCount() {
        return ledgerCount;
    }

    public void setLedgerCount(int v) {
        this.ledgerCount = v;
    }

    public int getStatementCount() {
        return statementCount;
    }

    public void setStatementCount(int v) {
        this.statementCount = v;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(int v) {
        this.matchedCount = v;
    }

    public int getDiscrepancyCount() {
        return discrepancyCount;
    }

    public void setDiscrepancyCount(int v) {
        this.discrepancyCount = v;
    }

    public int getMatchedByPassA() {
        return matchedByPassA;
    }

    public void setMatchedByPassA(int v) {
        this.matchedByPassA = v;
    }

    public int getMatchedByPassB() {
        return matchedByPassB;
    }

    public void setMatchedByPassB(int v) {
        this.matchedByPassB = v;
    }

    public int getMatchedByPassC() {
        return matchedByPassC;
    }

    public void setMatchedByPassC(int v) {
        this.matchedByPassC = v;
    }

    public int getMatchedByPassD() {
        return matchedByPassD;
    }

    public void setMatchedByPassD(int v) {
        this.matchedByPassD = v;
    }

    public long getLedgerGrossMinorUnits() {
        return ledgerGrossMinorUnits;
    }

    public void setLedgerGrossMinorUnits(long v) {
        this.ledgerGrossMinorUnits = v;
    }

    public long getStatementGrossMinorUnits() {
        return statementGrossMinorUnits;
    }

    public void setStatementGrossMinorUnits(long v) {
        this.statementGrossMinorUnits = v;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
