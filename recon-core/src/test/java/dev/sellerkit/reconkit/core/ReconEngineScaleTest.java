package dev.sellerkit.reconkit.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sellerkit.reconkit.core.model.ReconInput;
import dev.sellerkit.reconkit.core.model.ReconResult;
import dev.sellerkit.reconkit.domain.enums.MatchPass;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A day's worth of volume, reconciled in one call.
 *
 * <p>The number that matters is not the total, it is what happens when the identifier
 * column is unusable and everything falls through to the scoring pass. That is the night
 * the naive implementation, comparing every open row against every other, stops finishing
 * before the morning. Here the leftovers are indexed by amount first, so the comparison
 * count stays proportional to the rows that could plausibly match rather than to the
 * square of the file.
 */
class ReconEngineScaleTest {

    private static final int ROWS = 50_000;

    @Test
    @DisplayName("fifty thousand rows a side reconcile in a few seconds")
    void fullDay() {
        List<LedgerEntry> ledger = new ArrayList<>(ROWS);
        List<StatementEntry> statement = new ArrayList<>(ROWS);
        Random random = new Random(42);

        for (int index = 0; index < ROWS; index++) {
            long amount = 1_000L + random.nextInt(500) * 100L;
            int hour = random.nextInt(24);
            int minute = random.nextInt(60);
            ledger.add(Entries.ledger(index + 1, "T" + index, amount, hour, minute));
            // Two percent of the file is missing, which is a bad night by any standard.
            if (index % 50 != 0) {
                statement.add(Entries.statement(1_000_000 + index, "T" + index, amount, hour, minute));
            }
        }
        Collections.shuffle(statement, random);

        SettlementTerms terms = Entries.terms();
        long startedAt = System.nanoTime();
        ReconResult result = new ReconEngine().reconcile(
                new ReconInput(1L, Entries.DAY, Entries.SEOUL, "KRW", terms, ledger, statement));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(result.countFor(MatchPass.A_EXACT_ID)).isEqualTo(ROWS - ROWS / 50);
        assertThat(result.discrepancies()).hasSize(ROWS / 50);
        assertThat(elapsedMillis).isLessThan(10_000L);
        System.out.printf("reconciled %,d against %,d rows in %d ms%n", ledger.size(), statement.size(), elapsedMillis);
    }

    @Test
    @DisplayName("the same volume with no usable identifier still finishes, on the scoring pass alone")
    void identifierColumnUnusable() {
        int rows = 20_000;
        List<LedgerEntry> ledger = new ArrayList<>(rows);
        List<StatementEntry> statement = new ArrayList<>(rows);
        Random random = new Random(7);

        for (int index = 0; index < rows; index++) {
            long amount = 1_000L + index;
            int hour = random.nextInt(24);
            int minute = random.nextInt(60);
            LedgerEntry ledgerEntry = Entries.ledger(index + 1, null, amount, hour, minute);
            ledgerEntry.setApprovalNo(null);
            ledgerEntry.setOrderId(null);
            ledger.add(ledgerEntry);

            StatementEntry statementEntry = Entries.statement(1_000_000 + index, null, amount, hour, minute);
            statementEntry.setApprovalNo(null);
            statementEntry.setOrderId(null);
            statement.add(statementEntry);
        }
        Collections.shuffle(statement, random);

        long startedAt = System.nanoTime();
        ReconResult result = new ReconEngine().reconcile(
                new ReconInput(1L, Entries.DAY, Entries.SEOUL, "KRW", Entries.terms(), ledger, statement));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(result.countFor(MatchPass.C_FUZZY_AMOUNT_TIME)).isEqualTo(rows);
        assertThat(elapsedMillis).isLessThan(10_000L);
        System.out.printf("scoring pass matched %,d pairs in %d ms%n", rows, elapsedMillis);
    }
}
