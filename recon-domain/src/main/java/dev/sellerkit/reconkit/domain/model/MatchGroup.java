package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.MatchPass;
import dev.sellerkit.reconkit.domain.enums.MatchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One matching decision.
 *
 * <p>Every group records which pass produced it and with what confidence. An operator
 * looking at a settlement difference will ask why two rows were tied together, and a
 * matcher that cannot answer that question is a matcher nobody is allowed to trust
 * with money.
 */
@Entity
@Table(name = "match_group")
public class MatchGroup extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 24)
    private MatchType matchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "matched_by", nullable = false, length = 24)
    private MatchPass matchedBy;

    /** 1.0 for a key match. Below that for anything the engine had to infer. */
    @Column(name = "confidence", nullable = false)
    private double confidence;

    /** Human readable statement of the rule that fired. Shown verbatim in the UI. */
    @Column(name = "match_key", nullable = false, length = 200)
    private String matchKey;

    @Column(name = "ledger_gross_minor", nullable = false)
    private long ledgerGrossMinorUnits;

    @Column(name = "statement_gross_minor", nullable = false)
    private long statementGrossMinorUnits;

    @Column(name = "ledger_fee_minor", nullable = false)
    private long ledgerFeeMinorUnits;

    @Column(name = "statement_fee_minor", nullable = false)
    private long statementFeeMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    protected MatchGroup() {
    }

    public MatchGroup(Long runId, MatchType matchType, MatchPass matchedBy,
                      double confidence, String matchKey, String currency) {
        this.runId = runId;
        this.matchType = matchType;
        this.matchedBy = matchedBy;
        this.confidence = confidence;
        this.matchKey = matchKey;
        this.currency = currency;
    }

    public long grossDeltaMinorUnits() {
        return ledgerGrossMinorUnits - statementGrossMinorUnits;
    }

    public long feeDeltaMinorUnits() {
        return ledgerFeeMinorUnits - statementFeeMinorUnits;
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

    public MatchType getMatchType() {
        return matchType;
    }

    public MatchPass getMatchedBy() {
        return matchedBy;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getMatchKey() {
        return matchKey;
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

    public long getLedgerFeeMinorUnits() {
        return ledgerFeeMinorUnits;
    }

    public void setLedgerFeeMinorUnits(long v) {
        this.ledgerFeeMinorUnits = v;
    }

    public long getStatementFeeMinorUnits() {
        return statementFeeMinorUnits;
    }

    public void setStatementFeeMinorUnits(long v) {
        this.statementFeeMinorUnits = v;
    }

    public String getCurrency() {
        return currency;
    }
}
