package dev.sellerkit.reconkit.core;

import static dev.sellerkit.reconkit.core.Entries.DAY;
import static dev.sellerkit.reconkit.core.Entries.SEOUL;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sellerkit.reconkit.core.model.DiscrepancyDraft;
import dev.sellerkit.reconkit.core.model.ReconInput;
import dev.sellerkit.reconkit.core.model.ReconResult;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyType;
import dev.sellerkit.reconkit.domain.enums.MatchPass;
import dev.sellerkit.reconkit.domain.enums.MatchType;
import dev.sellerkit.reconkit.domain.enums.Severity;
import dev.sellerkit.reconkit.domain.enums.TxnStatus;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconEngineTest {

    private final ReconEngine engine = new ReconEngine();

    private ReconResult run(List<LedgerEntry> ledger, List<StatementEntry> statement) {
        return run(ledger, statement, Entries.terms());
    }

    private ReconResult run(List<LedgerEntry> ledger, List<StatementEntry> statement, SettlementTerms terms) {
        return engine.reconcile(new ReconInput(1L, DAY, SEOUL, "KRW", terms, ledger, statement));
    }

    @Test
    @DisplayName("identical days match on the transaction id and report nothing")
    void cleanDay() {
        ReconResult result = run(
                List.of(Entries.ledger(1, "T1", 10_000, 10, 0), Entries.ledger(2, "T2", 25_000, 11, 0)),
                List.of(Entries.statement(11, "T1", 10_000, 10, 0), Entries.statement(12, "T2", 25_000, 11, 0)));

        assertThat(result.matches()).hasSize(2);
        assertThat(result.countFor(MatchPass.A_EXACT_ID)).isEqualTo(2);
        assertThat(result.discrepancies()).isEmpty();
    }

    @Test
    @DisplayName("a ledger row with no statement line is reported as missing")
    void missingInStatement() {
        ReconResult result = run(
                List.of(Entries.ledger(1, "T1", 10_000, 10, 0), Entries.ledger(2, "T2", 250_000, 11, 0)),
                List.of(Entries.statement(11, "T1", 10_000, 10, 0)));

        assertThat(result.discrepancies())
                .extracting(DiscrepancyDraft::type)
                .containsExactly(DiscrepancyType.MISSING_IN_STATEMENT);
        assertThat(result.discrepancies().get(0).severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    @DisplayName("a transaction booked minutes before the cutoff is low severity, not a lost payment")
    void nearCutoffIsNotAnIncident() {
        ReconResult result = run(
                List.of(Entries.ledger(1, "T1", 990_000, 23, 20)),
                List.of());

        DiscrepancyDraft draft = result.discrepancies().get(0);
        assertThat(draft.type()).isEqualTo(DiscrepancyType.MISSING_IN_STATEMENT);
        assertThat(draft.severity()).isEqualTo(Severity.LOW);
        assertThat(draft.detail()).contains("cutoff");
    }

    @Test
    @DisplayName("the same transaction id twice in one file is a duplicate, before any matching happens")
    void duplicateStatementLine() {
        ReconResult result = run(
                List.of(Entries.ledger(1, "T1", 10_000, 10, 0)),
                List.of(Entries.statement(11, "T1", 10_000, 10, 0),
                        Entries.statement(12, "T1", 10_000, 10, 0)));

        assertThat(result.discrepancies())
                .extracting(DiscrepancyDraft::type)
                .contains(DiscrepancyType.DUPLICATE_STATEMENT, DiscrepancyType.MISSING_IN_LEDGER);
        assertThat(result.matches()).hasSize(1);
    }

    @Test
    @DisplayName("a regenerated transaction id still matches on approval number and amount")
    void compositeKeyRescuesARegeneratedId() {
        LedgerEntry ledger = Entries.ledger(1, "T1", 33_000, 9, 15);
        StatementEntry statement = Entries.statement(11, "REISSUED-9", 33_000, 9, 15);
        statement.setApprovalNo("AT1");

        ReconResult result = run(List.of(ledger), List.of(statement));

        assertThat(result.countFor(MatchPass.B_COMPOSITE_KEY)).isEqualTo(1);
        assertThat(result.matches().get(0).matchKey()).startsWith("approval no + amount");
        assertThat(result.discrepancies()).isEmpty();
    }

    @Test
    @DisplayName("a fee that differs from the contract is reported even though the gross agrees")
    void feeMismatch() {
        StatementEntry statement = Entries.statement(11, "T1", 1_000_000, 10, 0);
        statement.setFeeMinorUnits(statement.getFeeMinorUnits() + 5_000);

        ReconResult result = run(List.of(Entries.ledger(1, "T1", 1_000_000, 10, 0)), List.of(statement));

        assertThat(result.discrepancies())
                .extracting(DiscrepancyDraft::type)
                .containsExactly(DiscrepancyType.FEE_MISMATCH);
        assertThat(result.discrepancies().get(0).deltaMinor()).isEqualTo(-5_000L);
    }

    @Test
    @DisplayName("one won of rounding on a fee is inside tolerance and is not reported")
    void feeRoundingIsTolerated() {
        StatementEntry statement = Entries.statement(11, "T1", 33_333, 10, 0);
        statement.setFeeMinorUnits(statement.getFeeMinorUnits() + 1);

        ReconResult result = run(List.of(Entries.ledger(1, "T1", 33_333, 10, 0)), List.of(statement));

        assertThat(result.discrepancies()).isEmpty();
    }

    @Test
    @DisplayName("a refund on one side and a payment on the other is a status mismatch, not a match")
    void statusMismatch() {
        StatementEntry statement = Entries.statement(11, "T1", 10_000, 10, 0);
        statement.setTxnStatus(TxnStatus.REFUND);

        ReconResult result = run(List.of(Entries.ledger(1, "T1", 10_000, 10, 0)), List.of(statement));

        assertThat(result.discrepancies())
                .extracting(DiscrepancyDraft::type)
                .contains(DiscrepancyType.STATUS_MISMATCH);
    }

    @Test
    @DisplayName("three ledger rows summing to one payout line match as many to one")
    void aggregateMatch() {
        LedgerEntry first = Entries.ledger(1, null, 10_000, 9, 0);
        LedgerEntry second = Entries.ledger(2, null, 20_000, 9, 30);
        LedgerEntry third = Entries.ledger(3, null, 30_000, 10, 0);
        for (LedgerEntry entry : List.of(first, second, third)) {
            entry.setApprovalNo(null);
            entry.setOrderId("BATCH-77");
        }
        StatementEntry payout = Entries.statement(11, null, 60_000, 23, 0);
        payout.setApprovalNo(null);
        payout.setOrderId("BATCH-77");

        ReconResult result = run(List.of(first, second, third), List.of(payout));

        assertThat(result.countFor(MatchPass.D_AGGREGATE)).isEqualTo(1);
        assertThat(result.matches().get(0).type()).isEqualTo(MatchType.MANY_TO_ONE);
        assertThat(result.matches().get(0).ledgerEntryIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("an exact id match is never given up to a fuzzy pass that likes another pair better")
    void earlierPassWins() {
        LedgerEntry ledger = Entries.ledger(1, "T1", 10_000, 10, 0);
        StatementEntry exact = Entries.statement(11, "T1", 10_000, 15, 0);
        StatementEntry nearer = Entries.statement(12, "OTHER", 10_000, 10, 0);

        ReconResult result = run(List.of(ledger), List.of(exact, nearer));

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).statementEntryIds()).containsExactly(11L);
        assertThat(result.discrepancies())
                .extracting(DiscrepancyDraft::type)
                .containsExactly(DiscrepancyType.MISSING_IN_LEDGER);
    }

    @Test
    @DisplayName("the same input always produces the same output")
    void deterministic() {
        List<LedgerEntry> ledger = List.of(
                Entries.ledger(1, "T1", 10_000, 10, 0),
                Entries.ledger(2, null, 20_000, 10, 5),
                Entries.ledger(3, null, 20_000, 10, 6));
        List<StatementEntry> statement = List.of(
                Entries.statement(11, "T1", 10_000, 10, 0),
                Entries.statement(12, null, 20_000, 10, 7));

        ReconResult first = run(ledger, statement);
        ReconResult second = run(ledger, statement);

        assertThat(first.matches()).isEqualTo(second.matches());
        assertThat(first.discrepancies()).isEqualTo(second.discrepancies());
    }
}
