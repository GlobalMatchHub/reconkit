package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.SettlementStatus;
import dev.sellerkit.reconkit.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A settlement statement for one counterparty over one period.
 *
 * <p>The unique key on (counterparty, period start, period end) is the last line of
 * defence against paying the same period twice. Generation also takes a row lock, but
 * a constraint holds even when a future caller forgets the lock, and a duplicate payout
 * is not the kind of mistake worth trusting to convention.
 */
@Entity
@Table(name = "settlement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_settlement_period",
                columnNames = {"tenant_id", "counterparty_id", "period_start", "period_end"}))
public class Settlement extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    @Column(name = "statement_no", nullable = false, length = 40)
    private String statementNo;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "payout_date", nullable = false)
    private LocalDate payoutDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SettlementStatus status = SettlementStatus.DRAFT;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinorUnits;

    @Column(name = "refund_minor", nullable = false)
    private long refundMinorUnits;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinorUnits;

    @Column(name = "fee_vat_minor", nullable = false)
    private long feeVatMinorUnits;

    /** Manual corrections, typically last period's resolved discrepancies. */
    @Column(name = "adjustment_minor", nullable = false)
    private long adjustmentMinorUnits;

    @Column(name = "holdback_minor", nullable = false)
    private long holdbackMinorUnits;

    /** Holdback from earlier periods coming back this time. */
    @Column(name = "holdback_release_minor", nullable = false)
    private long holdbackReleaseMinorUnits;

    @Column(name = "net_payable_minor", nullable = false)
    private long netPayableMinorUnits;

    @Column(name = "txn_count", nullable = false)
    private int transactionCount;

    /** Open differences at generation time. A statement is not clean just because it balances. */
    @Column(name = "open_discrepancy_count", nullable = false)
    private int openDiscrepancyCount;

    @Column(name = "confirmed_by", length = 160)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Settlement() {
    }

    public Settlement(Long counterpartyId, String statementNo, LocalDate periodStart,
                      LocalDate periodEnd, LocalDate payoutDate, String currency) {
        this.counterpartyId = counterpartyId;
        this.statementNo = statementNo;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.payoutDate = payoutDate;
        this.currency = currency;
    }

    public Money netPayable() {
        return Money.of(netPayableMinorUnits, currency);
    }

    public void confirm(String actor) {
        if (status != SettlementStatus.DRAFT) {
            throw new IllegalStateException("only a draft settlement can be confirmed, was " + status);
        }
        this.status = SettlementStatus.CONFIRMED;
        this.confirmedBy = actor;
        this.confirmedAt = Instant.now();
    }

    public void voidStatement() {
        if (status == SettlementStatus.PAID) {
            throw new IllegalStateException("a paid settlement cannot be voided, issue a correction instead");
        }
        this.status = SettlementStatus.VOID;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Long getCounterpartyId() {
        return counterpartyId;
    }

    public String getStatementNo() {
        return statementNo;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public LocalDate getPayoutDate() {
        return payoutDate;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public long getGrossMinorUnits() {
        return grossMinorUnits;
    }

    public void setGrossMinorUnits(long v) {
        this.grossMinorUnits = v;
    }

    public long getRefundMinorUnits() {
        return refundMinorUnits;
    }

    public void setRefundMinorUnits(long v) {
        this.refundMinorUnits = v;
    }

    public long getFeeMinorUnits() {
        return feeMinorUnits;
    }

    public void setFeeMinorUnits(long v) {
        this.feeMinorUnits = v;
    }

    public long getFeeVatMinorUnits() {
        return feeVatMinorUnits;
    }

    public void setFeeVatMinorUnits(long v) {
        this.feeVatMinorUnits = v;
    }

    public long getAdjustmentMinorUnits() {
        return adjustmentMinorUnits;
    }

    public void setAdjustmentMinorUnits(long v) {
        this.adjustmentMinorUnits = v;
    }

    public long getHoldbackMinorUnits() {
        return holdbackMinorUnits;
    }

    public void setHoldbackMinorUnits(long v) {
        this.holdbackMinorUnits = v;
    }

    public long getHoldbackReleaseMinorUnits() {
        return holdbackReleaseMinorUnits;
    }

    public void setHoldbackReleaseMinorUnits(long v) {
        this.holdbackReleaseMinorUnits = v;
    }

    public long getNetPayableMinorUnits() {
        return netPayableMinorUnits;
    }

    public void setNetPayableMinorUnits(long v) {
        this.netPayableMinorUnits = v;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int v) {
        this.transactionCount = v;
    }

    public int getOpenDiscrepancyCount() {
        return openDiscrepancyCount;
    }

    public void setOpenDiscrepancyCount(int v) {
        this.openDiscrepancyCount = v;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
