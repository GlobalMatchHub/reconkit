package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.TxnStatus;
import dev.sellerkit.reconkit.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One transaction, on either side of the reconciliation.
 *
 * <p>Both sides carry the same shape on purpose. The engine compares like with like,
 * and every place the two schemas are allowed to drift apart becomes a special case
 * inside a matching rule. Normalisation happens once, at load time, not repeatedly
 * inside the matcher.
 */
@MappedSuperclass
public abstract class TransactionEntry extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    /** The counterparty's own identifier for the transaction. The strongest match key. */
    @Column(name = "external_txn_id", length = 80)
    private String externalTxnId;

    /** Card approval number. Second strongest key, and the one that survives retries. */
    @Column(name = "approval_no", length = 60)
    private String approvalNo;

    @Column(name = "order_id", length = 80)
    private String orderId;

    @Column(name = "merchant_no", length = 40)
    private String merchantNo;

    /** When the transaction actually happened, as an instant. No local time ambiguity. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * The settlement day this row belongs to, after the counterparty's cutoff has been
     * applied. This is a derived field, recomputed on load, never taken from the file.
     */
    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinorUnits;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinorUnits;

    @Column(name = "net_minor", nullable = false)
    private long netMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_status", nullable = false, length = 20)
    private TxnStatus txnStatus;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "card_bin", length = 8)
    private String cardBin;

    /** The original row, kept verbatim. An operator asking "what did the file say" gets an answer. */
    @Column(name = "raw_line", columnDefinition = "TEXT")
    private String rawLine;

    public Money gross() {
        return Money.of(grossMinorUnits, currency);
    }

    public Money fee() {
        return Money.of(feeMinorUnits, currency);
    }

    public Money net() {
        return Money.of(netMinorUnits, currency);
    }

    /** True when the transaction moves money away from us rather than towards us. */
    public boolean isReversal() {
        return txnStatus == TxnStatus.REFUND
                || txnStatus == TxnStatus.CANCEL
                || txnStatus == TxnStatus.CHARGEBACK;
    }

    /** Signed gross: reversals count negative so a day's total is a single sum. */
    public Money signedGross() {
        Money value = gross();
        return isReversal() ? value.abs().negate() : value.abs();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long v) {
        this.batchId = v;
    }

    public Long getCounterpartyId() {
        return counterpartyId;
    }

    public void setCounterpartyId(Long v) {
        this.counterpartyId = v;
    }

    public String getExternalTxnId() {
        return externalTxnId;
    }

    public void setExternalTxnId(String v) {
        this.externalTxnId = v;
    }

    public String getApprovalNo() {
        return approvalNo;
    }

    public void setApprovalNo(String v) {
        this.approvalNo = v;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String v) {
        this.orderId = v;
    }

    public String getMerchantNo() {
        return merchantNo;
    }

    public void setMerchantNo(String v) {
        this.merchantNo = v;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant v) {
        this.occurredAt = v;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate v) {
        this.businessDate = v;
    }

    public long getGrossMinorUnits() {
        return grossMinorUnits;
    }

    public void setGrossMinorUnits(long v) {
        this.grossMinorUnits = v;
    }

    public long getFeeMinorUnits() {
        return feeMinorUnits;
    }

    public void setFeeMinorUnits(long v) {
        this.feeMinorUnits = v;
    }

    public long getNetMinorUnits() {
        return netMinorUnits;
    }

    public void setNetMinorUnits(long v) {
        this.netMinorUnits = v;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String v) {
        this.currency = v;
    }

    public TxnStatus getTxnStatus() {
        return txnStatus;
    }

    public void setTxnStatus(TxnStatus v) {
        this.txnStatus = v;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String v) {
        this.paymentMethod = v;
    }

    public String getCardBin() {
        return cardBin;
    }

    public void setCardBin(String v) {
        this.cardBin = v;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String v) {
        this.rawLine = v;
    }
}
