package dev.sellerkit.reconkit.app.service;

import dev.sellerkit.reconkit.app.audit.AuditService;
import dev.sellerkit.reconkit.app.repo.CounterpartyRepository;
import dev.sellerkit.reconkit.app.repo.DiscrepancyRepository;
import dev.sellerkit.reconkit.app.repo.LedgerEntryRepository;
import dev.sellerkit.reconkit.app.repo.SettlementLineRepository;
import dev.sellerkit.reconkit.app.repo.SettlementLockRepository;
import dev.sellerkit.reconkit.app.repo.SettlementRepository;
import dev.sellerkit.reconkit.app.tenancy.TenantContext;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyStatus;
import dev.sellerkit.reconkit.domain.enums.SettlementStatus;
import dev.sellerkit.reconkit.domain.model.Counterparty;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.Settlement;
import dev.sellerkit.reconkit.domain.model.SettlementLine;
import dev.sellerkit.reconkit.domain.model.SettlementLock;
import dev.sellerkit.reconkit.settlement.DailyTotals;
import dev.sellerkit.reconkit.settlement.PayoutCalendar;
import dev.sellerkit.reconkit.settlement.SettlementAssembler;
import dev.sellerkit.reconkit.settlement.SettlementDraft;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces settlement statements.
 *
 * <p>Generation runs inside a database row lock keyed on the tenant, the counterparty and
 * the period. Everything about this operation is repeatable except its side effect: two
 * concurrent generations produce two statements for the same period, and if the second is
 * confirmed, the counterparty is paid twice. The lock is on a row rather than in the JVM
 * because the guard has to survive a second application instance.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);
    private static final List<DiscrepancyStatus> OPEN_STATUSES =
            List.of(DiscrepancyStatus.OPEN, DiscrepancyStatus.INVESTIGATING);

    private final SettlementAssembler assembler = new SettlementAssembler();
    private final PayoutCalendar calendar = PayoutCalendar.weekendsOnly();
    private final CounterpartyRepository counterparties;
    private final LedgerEntryRepository ledgerEntries;
    private final DiscrepancyRepository discrepancies;
    private final SettlementRepository settlements;
    private final SettlementLineRepository settlementLines;
    private final SettlementLockRepository locks;
    private final AuditService audit;

    public SettlementService(CounterpartyRepository counterparties,
                             LedgerEntryRepository ledgerEntries,
                             DiscrepancyRepository discrepancies,
                             SettlementRepository settlements,
                             SettlementLineRepository settlementLines,
                             SettlementLockRepository locks,
                             AuditService audit) {
        this.counterparties = counterparties;
        this.ledgerEntries = ledgerEntries;
        this.discrepancies = discrepancies;
        this.settlements = settlements;
        this.settlementLines = settlementLines;
        this.locks = locks;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Settlement generate(Long counterpartyId, LocalDate periodStart, LocalDate periodEnd) {
        Counterparty counterparty = counterparties.findById(counterpartyId)
                .orElseThrow(() -> new IllegalArgumentException("unknown counterparty " + counterpartyId));

        acquireLock(counterpartyId, periodStart, periodEnd);

        Settlement existing = settlements
                .findByCounterpartyIdAndPeriodStartAndPeriodEnd(counterpartyId, periodStart, periodEnd)
                .orElse(null);
        if (existing != null && existing.getStatus() != SettlementStatus.DRAFT) {
            throw new IllegalStateException(
                    "settlement %s for this period is %s and cannot be regenerated"
                            .formatted(existing.getStatementNo(), existing.getStatus()));
        }

        Map<LocalDate, DailyTotals> daily = new LinkedHashMap<>();
        List<LedgerEntry> entries = ledgerEntries
                .findByCounterpartyIdAndBusinessDateBetween(counterpartyId, periodStart, periodEnd);
        for (LedgerEntry entry : entries) {
            daily.computeIfAbsent(entry.getBusinessDate(),
                            date -> new DailyTotals(date, counterparty.getCurrency()))
                    .add(entry.signedGross(), counterparty.getTerms());
        }

        int openDifferences = (int) discrepancies.countByCounterpartyIdAndBusinessDateBetweenAndStatusIn(
                counterpartyId, periodStart, periodEnd, OPEN_STATUSES);

        String statementNo = existing != null
                ? existing.getStatementNo()
                : "ST-%s-%s".formatted(counterparty.getCode(), periodEnd.toString().replace("-", ""));

        SettlementDraft draft = assembler.assemble(
                counterpartyId, statementNo, periodStart, periodEnd, counterparty.getCurrency(),
                counterparty.getTerms(), calendar, daily, 0L, 0L, openDifferences);

        Settlement settlement = existing != null ? existing : new Settlement(
                counterpartyId, statementNo, periodStart, periodEnd, draft.payoutDate(), draft.currency());
        settlement.setGrossMinorUnits(draft.grossMinor());
        settlement.setRefundMinorUnits(draft.refundMinor());
        settlement.setFeeMinorUnits(draft.feeMinor());
        settlement.setFeeVatMinorUnits(draft.feeTaxMinor());
        settlement.setAdjustmentMinorUnits(draft.adjustmentMinor());
        settlement.setHoldbackMinorUnits(draft.holdbackMinor());
        settlement.setHoldbackReleaseMinorUnits(draft.holdbackReleaseMinor());
        settlement.setNetPayableMinorUnits(draft.netPayableMinor());
        settlement.setTransactionCount(draft.transactionCount());
        settlement.setOpenDiscrepancyCount(draft.openDiscrepancyCount());
        settlements.saveAndFlush(settlement);

        if (existing != null) {
            settlementLines.deleteBySettlementId(settlement.getId());
        }
        for (DailyTotals line : draft.lines()) {
            SettlementLine row = new SettlementLine(settlement.getId(), line.businessDate());
            row.setTransactionCount(line.transactionCount());
            row.setGrossMinorUnits(line.grossMinor());
            row.setRefundMinorUnits(line.refundMinor());
            row.setFeeMinorUnits(line.feeMinor());
            row.setFeeVatMinorUnits(line.feeTaxMinor());
            row.setNetMinorUnits(line.netMinor());
            settlementLines.save(row);
        }

        audit.record("SETTLEMENT_GENERATE", "Settlement", settlement.getId(), Map.of(
                "statementNo", statementNo,
                "period", periodStart + " to " + periodEnd,
                "netPayable", settlement.getNetPayableMinorUnits(),
                "openDifferences", openDifferences));

        log.info("settlement {} for {} covers {} transactions, net payable {}",
                statementNo, counterparty.getCode(), draft.transactionCount(), settlement.netPayable());
        return settlement;
    }

    @Transactional
    public Settlement confirm(Long settlementId) {
        Settlement settlement = settlements.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("unknown settlement " + settlementId));
        SettlementStatus before = settlement.getStatus();
        settlement.confirm(AuditService.currentActor());
        settlements.save(settlement);
        audit.record("SETTLEMENT_CONFIRM", "Settlement", settlementId,
                Map.of("status", before.name()), Map.of("status", settlement.getStatus().name()));
        return settlement;
    }

    /**
     * Takes the row lock, creating it on first use.
     *
     * <p>The insert and the lock are separate steps and the insert can lose a race, which
     * is fine: the loser catches the violation and locks the row the winner created, so
     * both end up queued on the same row rather than one of them failing.
     */
    private void acquireLock(Long counterpartyId, LocalDate periodStart, LocalDate periodEnd) {
        String key = "%s:%d:%s:%s".formatted(
                TenantContext.require(), counterpartyId, periodStart, periodEnd);
        if (locks.lock(key).isEmpty()) {
            try {
                locks.saveAndFlush(new SettlementLock(key));
            } catch (RuntimeException ex) {
                log.debug("lock row {} was created concurrently, waiting on it instead", key);
            }
            locks.lock(key).orElseThrow(() ->
                    new IllegalStateException("could not acquire the settlement lock for " + key));
        }
        log.debug("holding settlement lock {}", key);
    }
}
