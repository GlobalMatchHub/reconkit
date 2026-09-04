package dev.sellerkit.reconkit.core;

import dev.sellerkit.reconkit.core.model.DiscrepancyDraft;
import dev.sellerkit.reconkit.core.model.MatchDraft;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyType;
import dev.sellerkit.reconkit.domain.enums.Severity;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import dev.sellerkit.reconkit.domain.model.TransactionEntry;
import dev.sellerkit.reconkit.domain.money.Money;
import dev.sellerkit.reconkit.domain.policy.BusinessDateResolver;
import dev.sellerkit.reconkit.domain.policy.FeeCalculator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns what the matcher could not tie together, and what it tied together imperfectly,
 * into a queue a person can work through.
 *
 * <p>The severity rules are the point of this class. A queue where everything is urgent
 * gets ignored by the second week, so a difference that will resolve itself is not
 * allowed to look like a difference that will not. The clearest example is a transaction
 * that happened four minutes before the counterparty's cutoff and is absent from tonight's
 * file: that is not a lost payment, it is tomorrow's first row, and it is filed as low.
 * The same transaction absent from the middle of the afternoon is a real hole.
 */
public final class DiscrepancyClassifier {

    /** Below this, a difference is worth recording but not worth waking anyone for. */
    private static final long MINOR_THRESHOLD = 10_000L;
    private static final long MAJOR_THRESHOLD = 100_000L;
    private static final long CRITICAL_THRESHOLD = 1_000_000L;

    /** A gap this close to the cutoff is a timing artefact, not a missing transaction. */
    private static final long CUTOFF_GRACE_MINUTES = 30L;

    public List<DiscrepancyDraft> classifyDuplicates(List<? extends TransactionEntry> entries,
                                                     boolean ledgerSide) {
        Map<String, List<TransactionEntry>> byKey = new LinkedHashMap<>();
        for (TransactionEntry entry : entries) {
            String key = entry.getExternalTxnId();
            if (key != null && !key.isBlank()) {
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            }
        }
        List<DiscrepancyDraft> drafts = new ArrayList<>();
        byKey.forEach((key, group) -> {
            if (group.size() < 2) {
                return;
            }
            TransactionEntry duplicate = group.get(1);
            long amount = duplicate.signedGross().minorUnits();
            drafts.add(new DiscrepancyDraft(
                    ledgerSide ? DiscrepancyType.DUPLICATE_LEDGER : DiscrepancyType.DUPLICATE_STATEMENT,
                    Severity.HIGH,
                    ledgerSide ? amount : -amount,
                    duplicate.getCurrency(),
                    ledgerSide ? duplicate.getId() : null,
                    ledgerSide ? null : duplicate.getId(),
                    null,
                    "transaction id %s appears %d times in the same %s".formatted(
                            key, group.size(), ledgerSide ? "ledger load" : "statement file")));
        });
        return drafts;
    }

    /** Differences inside a pair the matcher was happy with. */
    public List<DiscrepancyDraft> classifyMatched(MatchState state, MatchDraft draft, int draftIndex) {
        List<DiscrepancyDraft> drafts = new ArrayList<>();
        SettlementTerms terms = state.input().terms();
        String currency = draft.currency();

        Long ledgerId = draft.ledgerEntryIds().size() == 1 ? draft.ledgerEntryIds().get(0) : null;
        Long statementId = draft.statementEntryIds().size() == 1 ? draft.statementEntryIds().get(0) : null;
        LedgerEntry ledger = ledgerId == null ? null : state.ledger(ledgerId);
        StatementEntry statement = statementId == null ? null : state.statement(statementId);

        if (ledger != null && statement != null && !ledger.getCurrency().equals(statement.getCurrency())) {
            drafts.add(new DiscrepancyDraft(
                    DiscrepancyType.CURRENCY_MISMATCH, Severity.CRITICAL,
                    0L, currency, ledgerId, statementId, draftIndex,
                    "ledger is in %s, statement is in %s".formatted(
                            ledger.getCurrency(), statement.getCurrency())));
            return drafts;
        }

        boolean statusDiffers = ledger != null && statement != null
                && ledger.getTxnStatus() != statement.getTxnStatus();
        if (statusDiffers) {
            // One finding, not three. A cancellation on one side and a payment on the
            // other necessarily disagrees on the amount and on the fee as well, and
            // raising all three turns a single problem into three queue items that an
            // operator has to work out are the same transaction.
            drafts.add(new DiscrepancyDraft(
                    DiscrepancyType.STATUS_MISMATCH, Severity.HIGH,
                    draft.grossDelta(), currency, ledgerId, statementId, draftIndex,
                    "we have %s, the statement has %s; the amount and fee differences follow from that"
                            .formatted(ledger.getTxnStatus(), statement.getTxnStatus())));
            return drafts;
        }

        long grossDelta = draft.grossDelta();
        if (!terms.amountTolerance().accepts(
                Money.of(draft.ledgerGrossMinor(), currency),
                Money.of(draft.statementGrossMinor(), currency))) {
            drafts.add(new DiscrepancyDraft(
                    DiscrepancyType.AMOUNT_MISMATCH,
                    severityOf(grossDelta),
                    grossDelta, currency, ledgerId, statementId, draftIndex,
                    "we booked %s, the statement says %s".formatted(
                            Money.of(draft.ledgerGrossMinor(), currency),
                            Money.of(draft.statementGrossMinor(), currency))));
        }

        Money expectedFee = FeeCalculator.totalFee(Money.of(draft.statementGrossMinor(), currency), terms);
        Money chargedFee = Money.of(draft.statementFeeMinor(), currency);
        if (chargedFee.minorUnits() != 0L && !terms.feeTolerance().accepts(expectedFee, chargedFee)) {
            long feeDelta = expectedFee.minorUnits() - chargedFee.minorUnits();
            drafts.add(new DiscrepancyDraft(
                    DiscrepancyType.FEE_MISMATCH,
                    severityOf(feeDelta),
                    feeDelta, currency, ledgerId, statementId, draftIndex,
                    "contract fee on %s is %s, the counterparty withheld %s".formatted(
                            Money.of(draft.statementGrossMinor(), currency), expectedFee, chargedFee)));
        }

        if (ledger != null && statement != null
                && !ledger.getBusinessDate().equals(statement.getBusinessDate())) {
            drafts.add(new DiscrepancyDraft(
                    DiscrepancyType.LATE_POSTING, Severity.LOW,
                    0L, currency, ledgerId, statementId, draftIndex,
                    "our business date is %s, the counterparty posted it on %s".formatted(
                            ledger.getBusinessDate(), statement.getBusinessDate())));
        }
        return drafts;
    }

    /** Rows no pass could place. */
    public List<DiscrepancyDraft> classifyUnmatched(MatchState state) {
        List<DiscrepancyDraft> drafts = new ArrayList<>();
        SettlementTerms terms = state.input().terms();
        BusinessDateResolver resolver = new BusinessDateResolver(state.input().zone(), terms.getCutoffTime());

        for (Long id : state.openLedgerIds()) {
            LedgerEntry ledger = state.ledger(id);
            // The ledger side is loaded across a window so that a statement line arriving
            // late can still find its transaction. A ledger row from a neighbouring date
            // that found no partner here belongs to its own date's run, and reporting it
            // on every run in the window turns one missing transaction into five.
            if (!ledger.getBusinessDate().equals(state.input().businessDate())) {
                continue;
            }
            long minutesToCutoff = resolver.minutesToCutoff(ledger.getOccurredAt());
            boolean nearCutoff = minutesToCutoff <= CUTOFF_GRACE_MINUTES;
            long amount = ledger.signedGross().minorUnits();
            drafts.add(new DiscrepancyDraft(
                    DiscrepancyType.MISSING_IN_STATEMENT,
                    nearCutoff ? Severity.LOW : severityOf(amount),
                    amount,
                    ledger.getCurrency(),
                    id, null, null,
                    nearCutoff
                            ? "booked %d minutes before the %s cutoff, expected on the next file"
                                    .formatted(minutesToCutoff, terms.getCutoffTime())
                            : "we booked %s, the statement has no matching line"
                                    .formatted(ledger.signedGross())));
        }

        for (Long id : state.openStatementIds()) {
            StatementEntry statement = state.statement(id);
            long amount = statement.signedGross().minorUnits();
            drafts.add(new DiscrepancyDraft(
                    DiscrepancyType.MISSING_IN_LEDGER,
                    // Money the counterparty says it moved and we have no record of is
                    // always worse than the reverse: we cannot even name the customer.
                    severityOf(amount) == Severity.LOW ? Severity.MEDIUM : severityOf(amount),
                    -amount,
                    statement.getCurrency(),
                    null, id, null,
                    "the statement reports %s with no matching record on our side"
                            .formatted(statement.signedGross())));
        }
        return drafts;
    }

    private static Severity severityOf(long deltaMinorUnits) {
        long magnitude = Math.abs(deltaMinorUnits);
        if (magnitude >= CRITICAL_THRESHOLD) {
            return Severity.CRITICAL;
        }
        if (magnitude >= MAJOR_THRESHOLD) {
            return Severity.HIGH;
        }
        if (magnitude >= MINOR_THRESHOLD) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }
}
