package dev.sellerkit.reconkit.app.service;

import dev.sellerkit.reconkit.app.audit.AuditService;
import dev.sellerkit.reconkit.app.repo.CounterpartyRepository;
import dev.sellerkit.reconkit.app.repo.DiscrepancyRepository;
import dev.sellerkit.reconkit.app.repo.LedgerEntryRepository;
import dev.sellerkit.reconkit.app.repo.MatchGroupRepository;
import dev.sellerkit.reconkit.app.repo.MatchMemberRepository;
import dev.sellerkit.reconkit.app.repo.ReconRunRepository;
import dev.sellerkit.reconkit.app.repo.StatementEntryRepository;
import dev.sellerkit.reconkit.app.repo.TenantRepository;
import dev.sellerkit.reconkit.app.tenancy.TenantContext;
import dev.sellerkit.reconkit.core.ReconEngine;
import dev.sellerkit.reconkit.core.model.DiscrepancyDraft;
import dev.sellerkit.reconkit.core.model.MatchDraft;
import dev.sellerkit.reconkit.core.model.ReconInput;
import dev.sellerkit.reconkit.core.model.ReconResult;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyStatus;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyType;
import dev.sellerkit.reconkit.domain.enums.EntrySide;
import dev.sellerkit.reconkit.domain.enums.MatchPass;
import dev.sellerkit.reconkit.domain.model.Counterparty;
import dev.sellerkit.reconkit.domain.model.Discrepancy;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.MatchGroup;
import dev.sellerkit.reconkit.domain.model.MatchMember;
import dev.sellerkit.reconkit.domain.model.ReconRun;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a day, runs the engine, writes the result.
 *
 * <p>The service does the fetching and the persisting; the engine does the deciding.
 * Keeping those apart is what makes a disputed night re-runnable: the same rows produce
 * the same answer, and nothing about the answer depends on what else was happening in the
 * database at the time.
 */
@Service
public class ReconService {

    private static final Logger log = LoggerFactory.getLogger(ReconService.class);
    private static final List<DiscrepancyStatus> OPEN_STATUSES =
            List.of(DiscrepancyStatus.OPEN, DiscrepancyStatus.INVESTIGATING);

    private final ReconEngine engine = new ReconEngine();
    private final CounterpartyRepository counterparties;
    private final LedgerEntryRepository ledgerEntries;
    private final StatementEntryRepository statementEntries;
    private final ReconRunRepository runs;
    private final MatchGroupRepository matchGroups;
    private final MatchMemberRepository matchMembers;
    private final DiscrepancyRepository discrepancies;
    private final TenantRepository tenants;
    private final AuditService audit;

    public ReconService(CounterpartyRepository counterparties,
                        LedgerEntryRepository ledgerEntries,
                        StatementEntryRepository statementEntries,
                        ReconRunRepository runs,
                        MatchGroupRepository matchGroups,
                        MatchMemberRepository matchMembers,
                        DiscrepancyRepository discrepancies,
                        TenantRepository tenants,
                        AuditService audit) {
        this.counterparties = counterparties;
        this.ledgerEntries = ledgerEntries;
        this.statementEntries = statementEntries;
        this.runs = runs;
        this.matchGroups = matchGroups;
        this.matchMembers = matchMembers;
        this.discrepancies = discrepancies;
        this.tenants = tenants;
        this.audit = audit;
    }

    @Transactional
    public ReconRun run(Long counterpartyId, LocalDate businessDate) {
        Counterparty counterparty = counterparties.findById(counterpartyId)
                .orElseThrow(() -> new IllegalArgumentException("unknown counterparty " + counterpartyId));
        ZoneId zone = tenants.findById(TenantContext.require())
                .map(t -> ZoneId.of(t.getTimeZone()))
                .orElse(ZoneId.of("Asia/Seoul"));

        ReconRun run = openRun(counterpartyId, businessDate);
        run.start();
        runs.save(run);

        try {
            // A run is anchored to one statement date and reaches across a window on our
            // own side. That direction matters. The counterparty's file is what arrives
            // tonight and what has to be signed off tonight; our ledger is already there
            // and can be searched backwards, so a line the counterparty posted a day late
            // still finds its transaction. Widening the statement side instead would pull
            // the same line into every run in the window and report it repeatedly.
            int window = Math.max(1, counterparty.getTerms().getMatchWindowDays());
            List<LedgerEntry> ledger = ledgerEntries.findByCounterpartyIdAndBusinessDateBetween(
                    counterpartyId, businessDate.minusDays(window), businessDate.plusDays(window));
            List<StatementEntry> statement =
                    statementEntries.findByCounterpartyIdAndBusinessDate(counterpartyId, businessDate);

            ReconResult result = engine.reconcile(new ReconInput(
                    counterpartyId, businessDate, zone, counterparty.getCurrency(),
                    counterparty.getTerms(), ledger, statement));

            persist(run, result);
            closeExplainedDiscrepancies(run, counterpartyId, businessDate, window, result);

            run.setLedgerCount(result.ledgerCount());
            run.setStatementCount(result.statementCount());
            run.setMatchedCount(result.matchedEntryCount());
            run.setDiscrepancyCount(result.discrepancies().size());
            run.setMatchedByPassA(result.countFor(MatchPass.A_EXACT_ID));
            run.setMatchedByPassB(result.countFor(MatchPass.B_COMPOSITE_KEY));
            run.setMatchedByPassC(result.countFor(MatchPass.C_FUZZY_AMOUNT_TIME));
            run.setMatchedByPassD(result.countFor(MatchPass.D_AGGREGATE));
            run.setLedgerGrossMinorUnits(result.ledgerGrossMinor());
            run.setStatementGrossMinorUnits(result.statementGrossMinor());
            run.complete();
            runs.save(run);

            audit.record("RECON_RUN", "ReconRun", run.getId(), Map.of(
                    "counterpartyId", counterpartyId,
                    "businessDate", businessDate.toString(),
                    "sequence", run.getSequenceNo(),
                    "matched", result.matchedEntryCount(),
                    "discrepancies", result.discrepancies().size()));

            log.info("run {} for counterparty {} on {} matched {} of {} rows with {} differences",
                    run.getId(), counterpartyId, businessDate, result.matchedEntryCount(),
                    result.ledgerCount() + result.statementCount(), result.discrepancies().size());
            return run;
        } catch (RuntimeException ex) {
            run.fail(ex.toString());
            runs.save(run);
            throw ex;
        }
    }

    /**
     * Claims the next sequence number for the date.
     *
     * <p>The unique key does the arbitration. Two schedulers that fire together both read
     * the same latest sequence and both try to insert the next one; one succeeds and the
     * other is handed the winner's run rather than producing a second set of numbers for
     * the same night.
     */
    private ReconRun openRun(Long counterpartyId, LocalDate businessDate) {
        int next = runs.findTopByCounterpartyIdAndBusinessDateOrderBySequenceNoDesc(counterpartyId, businessDate)
                .map(existing -> existing.getSequenceNo() + 1)
                .orElse(1);
        try {
            return runs.saveAndFlush(
                    new ReconRun(counterpartyId, businessDate, next, ReconEngine.RULE_VERSION));
        } catch (DataIntegrityViolationException ex) {
            log.info("sequence {} for counterparty {} on {} was taken by another runner",
                    next, counterpartyId, businessDate);
            return runs.findTopByCounterpartyIdAndBusinessDateOrderBySequenceNoDesc(counterpartyId, businessDate)
                    .orElseThrow(() -> ex);
        }
    }

    private void persist(ReconRun run, ReconResult result) {
        List<MatchGroup> savedGroups = new ArrayList<>(result.matches().size());
        List<MatchMember> members = new ArrayList<>();

        for (MatchDraft draft : result.matches()) {
            MatchGroup group = new MatchGroup(run.getId(), draft.type(), draft.pass(),
                    draft.confidence(), draft.matchKey(), draft.currency());
            group.setLedgerGrossMinorUnits(draft.ledgerGrossMinor());
            group.setStatementGrossMinorUnits(draft.statementGrossMinor());
            group.setLedgerFeeMinorUnits(draft.ledgerFeeMinor());
            group.setStatementFeeMinorUnits(draft.statementFeeMinor());
            savedGroups.add(matchGroups.save(group));
        }
        for (int index = 0; index < result.matches().size(); index++) {
            MatchDraft draft = result.matches().get(index);
            Long groupId = savedGroups.get(index).getId();
            draft.ledgerEntryIds().forEach(id ->
                    members.add(new MatchMember(groupId, EntrySide.LEDGER, id)));
            draft.statementEntryIds().forEach(id ->
                    members.add(new MatchMember(groupId, EntrySide.STATEMENT, id)));
        }
        matchMembers.saveAll(members);

        List<Discrepancy> rows = new ArrayList<>(result.discrepancies().size());
        for (DiscrepancyDraft draft : result.discrepancies()) {
            Discrepancy discrepancy = new Discrepancy(
                    run.getId(), run.getCounterpartyId(), run.getBusinessDate(),
                    draft.type(), draft.severity(), draft.deltaMinor(), draft.currency(), draft.detail());
            discrepancy.setLedgerEntryId(draft.ledgerEntryId());
            discrepancy.setStatementEntryId(draft.statementEntryId());
            if (draft.matchDraftIndex() != null) {
                discrepancy.setGroupId(savedGroups.get(draft.matchDraftIndex()).getId());
            }
            rows.add(discrepancy);
        }
        discrepancies.saveAll(rows);
    }

    /**
     * Closes yesterday's differences that this run has explained.
     *
     * <p>Almost every late posting resolves itself on the next file. Leaving those rows
     * open means the queue grows by the same forty transactions every night until nobody
     * reads it, so the run that finds the missing side closes the row and records which
     * run did it. Auto closing is written down, never silent.
     */
    private void closeExplainedDiscrepancies(ReconRun run, Long counterpartyId,
                                             LocalDate businessDate, int window, ReconResult result) {
        Set<Long> matchedLedger = new HashSet<>();
        Set<Long> matchedStatement = new HashSet<>();
        for (MatchDraft draft : result.matches()) {
            matchedLedger.addAll(draft.ledgerEntryIds());
            matchedStatement.addAll(draft.statementEntryIds());
        }

        // Tonight's file explains yesterday's gap, so the search covers the same window
        // the matcher used rather than only today.
        List<Discrepancy> previouslyOpen = discrepancies
                .findByStatusInAndCounterpartyIdAndBusinessDateBetween(
                        OPEN_STATUSES, counterpartyId,
                        businessDate.minusDays(window), businessDate.plusDays(window));
        int closed = 0;
        for (Discrepancy discrepancy : previouslyOpen) {
            if (discrepancy.getRunId().equals(run.getId())) {
                continue;
            }
            boolean explained =
                    (discrepancy.getType() == DiscrepancyType.MISSING_IN_STATEMENT
                            && discrepancy.getLedgerEntryId() != null
                            && matchedLedger.contains(discrepancy.getLedgerEntryId()))
                            || (discrepancy.getType() == DiscrepancyType.MISSING_IN_LEDGER
                            && discrepancy.getStatementEntryId() != null
                            && matchedStatement.contains(discrepancy.getStatementEntryId()));
            if (explained) {
                discrepancy.autoClose(run.getId(), "matched by run " + run.getId());
                closed++;
            }
        }
        if (closed > 0) {
            discrepancies.saveAll(previouslyOpen);
            log.info("run {} closed {} difference(s) raised by an earlier run", run.getId(), closed);
        }
    }
}
