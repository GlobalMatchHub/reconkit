package dev.sellerkit.reconkit.core.pass;

import dev.sellerkit.reconkit.core.MatchState;
import dev.sellerkit.reconkit.core.model.MatchDraft;
import dev.sellerkit.reconkit.domain.enums.MatchPass;
import dev.sellerkit.reconkit.domain.enums.MatchType;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import dev.sellerkit.reconkit.domain.money.Money;
import dev.sellerkit.reconkit.domain.money.Tolerance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pass C. What is left after the keys have been used: near amounts, near times.
 *
 * <p>Two decisions carry this pass.
 *
 * <p>The first is that candidates are found through an index on the signed amount rather
 * than by comparing every open row with every other. The leftovers after passes A and B
 * are a small fraction of the day, but on a bad night, when a counterparty ships a file
 * with a regenerated identifier column, the leftovers are the whole day, and that is the
 * night the quadratic version stops finishing before the morning.
 *
 * <p>The second is that scoring is global, not greedy per row. Every candidate pair is
 * scored, then the whole list is taken best first. Walking the ledger in order and giving
 * each row its own favourite lets an early row take a statement line that a later row
 * matched far better, and the resulting difference is reported against the wrong
 * transaction, which is worse than reporting nothing.
 */
public final class FuzzyAmountTimePass implements MatchingPass {

    private static final double MIN_CONFIDENCE = 0.55d;

    @Override
    public MatchPass id() {
        return MatchPass.C_FUZZY_AMOUNT_TIME;
    }

    @Override
    public void apply(MatchState state) {
        SettlementTerms terms = state.input().terms();
        Tolerance tolerance = terms.amountTolerance();
        Duration window = Duration.ofDays(Math.max(1, terms.getMatchWindowDays()));

        NavigableMap<Long, List<Long>> statementsByAmount = new TreeMap<>();
        for (Long id : state.openStatementIds()) {
            long amount = state.statement(id).signedGross().minorUnits();
            statementsByAmount.computeIfAbsent(amount, k -> new ArrayList<>()).add(id);
        }
        if (statementsByAmount.isEmpty()) {
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Long ledgerId : state.openLedgerIds()) {
            LedgerEntry ledger = state.ledger(ledgerId);
            long amount = ledger.signedGross().minorUnits();
            long slack = allowedSlack(amount, tolerance);
            for (List<Long> bucket : statementsByAmount.subMap(amount - slack, true, amount + slack, true).values()) {
                for (Long statementId : bucket) {
                    StatementEntry statement = state.statement(statementId);
                    if (!statement.getCurrency().equals(ledger.getCurrency())) {
                        continue;
                    }
                    Duration apart = Duration.between(ledger.getOccurredAt(), statement.getOccurredAt()).abs();
                    if (apart.compareTo(window) > 0) {
                        continue;
                    }
                    double score = score(ledger, statement, tolerance, window, apart);
                    if (score >= MIN_CONFIDENCE) {
                        candidates.add(new Candidate(ledgerId, statementId, score, apart));
                    }
                }
            }
        }

        candidates.sort(Comparator
                .comparingDouble(Candidate::score).reversed()
                .thenComparing(c -> c.apart));

        Set<Long> takenLedger = new HashSet<>();
        Set<Long> takenStatement = new HashSet<>();
        List<MatchDraft> drafts = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!takenLedger.add(candidate.ledgerId)) {
                continue;
            }
            if (!takenStatement.add(candidate.statementId)) {
                takenLedger.remove(candidate.ledgerId);
                continue;
            }
            LedgerEntry ledger = state.ledger(candidate.ledgerId);
            StatementEntry statement = state.statement(candidate.statementId);
            drafts.add(new MatchDraft(
                    MatchType.ONE_TO_ONE,
                    MatchPass.C_FUZZY_AMOUNT_TIME,
                    round(candidate.score),
                    "amount within tolerance, %d min apart".formatted(candidate.apart.toMinutes()),
                    List.of(candidate.ledgerId),
                    List.of(candidate.statementId),
                    ledger.signedGross().minorUnits(),
                    statement.signedGross().minorUnits(),
                    ledger.getFeeMinorUnits(),
                    statement.getFeeMinorUnits(),
                    ledger.getCurrency()));
        }
        drafts.forEach(state::accept);
    }

    private static long allowedSlack(long amount, Tolerance tolerance) {
        long relative = (Math.abs(amount) * tolerance.relativeBasisPoints() + 5_000L) / 10_000L;
        return Math.max(tolerance.absoluteMinorUnits(), relative);
    }

    /**
     * Amount closeness carries most of the weight, time closeness the rest, and matching
     * payment details add a small bonus. The weights are stated here rather than tuned
     * into a constant somewhere, because every one of them is an argument the operator
     * may have to repeat to a counterparty.
     */
    private static double score(LedgerEntry ledger, StatementEntry statement,
                                Tolerance tolerance, Duration window, Duration apart) {
        Money ledgerAmount = ledger.signedGross();
        Money statementAmount = statement.signedGross();
        long difference = Math.abs(ledgerAmount.minorUnits() - statementAmount.minorUnits());
        long slack = Math.max(1L, allowedSlack(ledgerAmount.minorUnits(), tolerance));
        double amountScore = 1.0d - Math.min(1.0d, (double) difference / (double) slack);

        double timeScore = 1.0d - Math.min(1.0d,
                (double) apart.toSeconds() / (double) Math.max(1L, window.toSeconds()));

        double bonus = 0.0d;
        if (equalsIgnoreNull(ledger.getPaymentMethod(), statement.getPaymentMethod())) {
            bonus += 0.05d;
        }
        if (equalsIgnoreNull(ledger.getCardBin(), statement.getCardBin())) {
            bonus += 0.05d;
        }
        if (ledger.getTxnStatus() == statement.getTxnStatus()) {
            bonus += 0.05d;
        }
        return Math.min(0.94d, 0.60d * amountScore + 0.25d * timeScore + bonus);
    }

    private static boolean equalsIgnoreNull(String left, String right) {
        return left != null && right != null && left.equals(right);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0d) / 1000.0d;
    }

    private record Candidate(Long ledgerId, Long statementId, double score, Duration apart) {
    }
}
