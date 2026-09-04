package dev.sellerkit.reconkit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** One day of a settlement period, broken out so the total can be traced back. */
@Entity
@Table(name = "settlement_line")
public class SettlementLine extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "txn_count", nullable = false)
    private int transactionCount;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinorUnits;

    @Column(name = "refund_minor", nullable = false)
    private long refundMinorUnits;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinorUnits;

    @Column(name = "fee_vat_minor", nullable = false)
    private long feeVatMinorUnits;

    @Column(name = "net_minor", nullable = false)
    private long netMinorUnits;

    protected SettlementLine() {
    }

    public SettlementLine(Long settlementId, LocalDate businessDate) {
        this.settlementId = settlementId;
        this.businessDate = businessDate;
    }

    public Long getId() {
        return id;
    }

    public Long getSettlementId() {
        return settlementId;
    }

    public void setSettlementId(Long v) {
        this.settlementId = v;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int v) {
        this.transactionCount = v;
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

    public long getNetMinorUnits() {
        return netMinorUnits;
    }

    public void setNetMinorUnits(long v) {
        this.netMinorUnits = v;
    }
}
