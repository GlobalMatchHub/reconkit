package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.SettlementCycle;
import dev.sellerkit.reconkit.domain.money.Tolerance;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalTime;

/**
 * The commercial contract with one counterparty, in a form the engine can compute with.
 *
 * <p>{@code cutoffTime} deserves the attention. Our own systems roll the day at
 * midnight local time; a gateway rolls it at its own cutoff, often half an hour
 * earlier. Transactions between the two clocks land on our books one day and on
 * theirs the next. That single field is the source of most so called late postings,
 * and without it the engine reports a pile of missing rows every night at the same hour.
 */
@Embeddable
public class SettlementTerms {

    /** Commission rate in basis points. 220 means 2.20 percent. */
    @Column(name = "fee_rate_bp", nullable = false)
    private int feeRateBasisPoints;

    /** Flat fee per transaction, in minor units, charged on top of the rate. */
    @Column(name = "fixed_fee_minor", nullable = false)
    private long fixedFeeMinorUnits;

    /** VAT applied to the commission itself, in basis points. 1000 means 10 percent. */
    @Column(name = "fee_vat_bp", nullable = false)
    private int feeVatBasisPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle", nullable = false, length = 24)
    private SettlementCycle cycle = SettlementCycle.DAILY_T_PLUS_N;

    /** Business days between the transaction date and the payout date. */
    @Column(name = "settle_after_days", nullable = false)
    private int settleAfterDays = 2;

    /** Share of the net amount held back against future refunds, in basis points. */
    @Column(name = "holdback_bp", nullable = false)
    private int holdbackBasisPoints;

    /** Days the holdback is retained before it is released back to the merchant. */
    @Column(name = "holdback_release_days", nullable = false)
    private int holdbackReleaseDays = 30;

    /** The counterparty's own day boundary, in the tenant's time zone. */
    @Column(name = "cutoff_time", nullable = false)
    private LocalTime cutoffTime = LocalTime.MIDNIGHT;

    @Column(name = "amount_tol_abs", nullable = false)
    private long amountToleranceAbsolute;

    @Column(name = "amount_tol_bp", nullable = false)
    private int amountToleranceBasisPoints;

    @Column(name = "fee_tol_abs", nullable = false)
    private long feeToleranceAbsolute = 1L;

    @Column(name = "fee_tol_bp", nullable = false)
    private int feeToleranceBasisPoints = 10;

    /** How many days on either side of our date a statement row may still match. */
    @Column(name = "match_window_days", nullable = false)
    private int matchWindowDays = 2;

    public SettlementTerms() {
    }

    public Tolerance amountTolerance() {
        return new Tolerance(amountToleranceAbsolute, amountToleranceBasisPoints);
    }

    public Tolerance feeTolerance() {
        return new Tolerance(feeToleranceAbsolute, feeToleranceBasisPoints);
    }

    public int getFeeRateBasisPoints() {
        return feeRateBasisPoints;
    }

    public void setFeeRateBasisPoints(int v) {
        this.feeRateBasisPoints = v;
    }

    public long getFixedFeeMinorUnits() {
        return fixedFeeMinorUnits;
    }

    public void setFixedFeeMinorUnits(long v) {
        this.fixedFeeMinorUnits = v;
    }

    public int getFeeVatBasisPoints() {
        return feeVatBasisPoints;
    }

    public void setFeeVatBasisPoints(int v) {
        this.feeVatBasisPoints = v;
    }

    public SettlementCycle getCycle() {
        return cycle;
    }

    public void setCycle(SettlementCycle v) {
        this.cycle = v;
    }

    public int getSettleAfterDays() {
        return settleAfterDays;
    }

    public void setSettleAfterDays(int v) {
        this.settleAfterDays = v;
    }

    public int getHoldbackBasisPoints() {
        return holdbackBasisPoints;
    }

    public void setHoldbackBasisPoints(int v) {
        this.holdbackBasisPoints = v;
    }

    public int getHoldbackReleaseDays() {
        return holdbackReleaseDays;
    }

    public void setHoldbackReleaseDays(int v) {
        this.holdbackReleaseDays = v;
    }

    public LocalTime getCutoffTime() {
        return cutoffTime;
    }

    public void setCutoffTime(LocalTime v) {
        this.cutoffTime = v;
    }

    public long getAmountToleranceAbsolute() {
        return amountToleranceAbsolute;
    }

    public void setAmountToleranceAbsolute(long v) {
        this.amountToleranceAbsolute = v;
    }

    public int getAmountToleranceBasisPoints() {
        return amountToleranceBasisPoints;
    }

    public void setAmountToleranceBasisPoints(int v) {
        this.amountToleranceBasisPoints = v;
    }

    public long getFeeToleranceAbsolute() {
        return feeToleranceAbsolute;
    }

    public void setFeeToleranceAbsolute(long v) {
        this.feeToleranceAbsolute = v;
    }

    public int getFeeToleranceBasisPoints() {
        return feeToleranceBasisPoints;
    }

    public void setFeeToleranceBasisPoints(int v) {
        this.feeToleranceBasisPoints = v;
    }

    public int getMatchWindowDays() {
        return matchWindowDays;
    }

    public void setMatchWindowDays(int v) {
        this.matchWindowDays = v;
    }
}
