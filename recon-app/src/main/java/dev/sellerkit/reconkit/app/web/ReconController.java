package dev.sellerkit.reconkit.app.web;

import dev.sellerkit.reconkit.app.repo.CounterpartyRepository;
import dev.sellerkit.reconkit.app.repo.LedgerEntryRepository;
import dev.sellerkit.reconkit.app.repo.MatchGroupRepository;
import dev.sellerkit.reconkit.app.repo.MatchMemberRepository;
import dev.sellerkit.reconkit.app.repo.ReconRunRepository;
import dev.sellerkit.reconkit.app.repo.StatementEntryRepository;
import dev.sellerkit.reconkit.app.service.ReconService;
import dev.sellerkit.reconkit.domain.enums.EntrySide;
import dev.sellerkit.reconkit.domain.model.MatchGroup;
import dev.sellerkit.reconkit.domain.model.MatchMember;
import dev.sellerkit.reconkit.domain.model.ReconRun;
import dev.sellerkit.reconkit.domain.model.TransactionEntry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recon")
public class ReconController {

    private final ReconService reconService;
    private final ReconRunRepository runs;
    private final MatchGroupRepository matchGroups;
    private final MatchMemberRepository matchMembers;
    private final LedgerEntryRepository ledgerEntries;
    private final StatementEntryRepository statementEntries;
    private final CounterpartyRepository counterparties;

    public ReconController(ReconService reconService, ReconRunRepository runs,
                           MatchGroupRepository matchGroups, MatchMemberRepository matchMembers,
                           LedgerEntryRepository ledgerEntries, StatementEntryRepository statementEntries,
                           CounterpartyRepository counterparties) {
        this.reconService = reconService;
        this.runs = runs;
        this.matchGroups = matchGroups;
        this.matchMembers = matchMembers;
        this.ledgerEntries = ledgerEntries;
        this.statementEntries = statementEntries;
        this.counterparties = counterparties;
    }

    public record RunRequest(Long counterpartyId,
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
    }

    public record RunView(Long id, Long counterpartyId, String counterpartyName, LocalDate businessDate,
                          int sequenceNo, String status, String ruleVersion,
                          int ledgerCount, int statementCount, int matchedCount, int discrepancyCount,
                          double matchRate, int passA, int passB, int passC, int passD,
                          long ledgerGrossMinor, long statementGrossMinor, Long durationMillis,
                          String currency) {
    }

    public record EntryView(Long id, String side, String externalTxnId, String approvalNo, String orderId,
                            String occurredAt, LocalDate businessDate, long grossMinor, long feeMinor,
                            String currency, String txnStatus, String paymentMethod) {

        static EntryView of(TransactionEntry entry, EntrySide side) {
            return new EntryView(entry.getId(), side.name(), entry.getExternalTxnId(), entry.getApprovalNo(),
                    entry.getOrderId(), entry.getOccurredAt().toString(), entry.getBusinessDate(),
                    entry.signedGross().minorUnits(), entry.getFeeMinorUnits(), entry.getCurrency(),
                    entry.getTxnStatus().name(), entry.getPaymentMethod());
        }
    }

    public record MatchView(Long id, String matchType, String matchedBy, double confidence, String matchKey,
                            long ledgerGrossMinor, long statementGrossMinor, long grossDelta,
                            long ledgerFeeMinor, long statementFeeMinor, String currency,
                            List<EntryView> ledgerEntries, List<EntryView> statementEntries) {
    }

    @PostMapping("/runs")
    public RunView run(@RequestBody RunRequest request) {
        return toView(reconService.run(request.counterpartyId(), request.businessDate()));
    }

    @GetMapping("/runs")
    public List<RunView> list(@RequestParam(defaultValue = "50") int size) {
        return runs.findAllByOrderByIdDesc(PageRequest.of(0, Math.min(size, 200)))
                .stream().map(this::toView).toList();
    }

    @GetMapping("/runs/{id}")
    public RunView one(@PathVariable Long id) {
        return toView(runs.findById(id).orElseThrow(() -> new IllegalArgumentException("unknown run " + id)));
    }

    /**
     * The match explorer. Members are fetched in one query for the whole page rather than
     * per group, because a page of thirty aggregate matches otherwise turns into a
     * hundred and fifty round trips for a screen that shows one table.
     */
    @GetMapping("/runs/{id}/matches")
    public List<MatchView> matches(@PathVariable Long id,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        List<MatchGroup> groups = matchGroups
                .findByRunIdOrderByIdAsc(id, PageRequest.of(page, Math.min(size, 200)))
                .getContent();
        if (groups.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = groups.stream().map(MatchGroup::getId).toList();
        List<MatchMember> members = matchMembers.findByGroupIdIn(groupIds);

        Map<Long, List<Long>> ledgerIdsByGroup = new HashMap<>();
        Map<Long, List<Long>> statementIdsByGroup = new HashMap<>();
        for (MatchMember member : members) {
            Map<Long, List<Long>> target = member.getSide() == EntrySide.LEDGER
                    ? ledgerIdsByGroup : statementIdsByGroup;
            target.computeIfAbsent(member.getGroupId(), key -> new ArrayList<>()).add(member.getEntryId());
        }

        Map<Long, TransactionEntry> ledgerById = new HashMap<>();
        ledgerEntries.findAllById(ledgerIdsByGroup.values().stream().flatMap(List::stream).toList())
                .forEach(entry -> ledgerById.put(entry.getId(), entry));
        Map<Long, TransactionEntry> statementById = new HashMap<>();
        statementEntries.findAllById(statementIdsByGroup.values().stream().flatMap(List::stream).toList())
                .forEach(entry -> statementById.put(entry.getId(), entry));

        List<MatchView> views = new ArrayList<>(groups.size());
        for (MatchGroup group : groups) {
            views.add(new MatchView(group.getId(), group.getMatchType().name(), group.getMatchedBy().name(),
                    group.getConfidence(), group.getMatchKey(),
                    group.getLedgerGrossMinorUnits(), group.getStatementGrossMinorUnits(),
                    group.grossDeltaMinorUnits(), group.getLedgerFeeMinorUnits(),
                    group.getStatementFeeMinorUnits(), group.getCurrency(),
                    ledgerIdsByGroup.getOrDefault(group.getId(), List.of()).stream()
                            .map(ledgerById::get).filter(java.util.Objects::nonNull)
                            .map(entry -> EntryView.of(entry, EntrySide.LEDGER)).toList(),
                    statementIdsByGroup.getOrDefault(group.getId(), List.of()).stream()
                            .map(statementById::get).filter(java.util.Objects::nonNull)
                            .map(entry -> EntryView.of(entry, EntrySide.STATEMENT)).toList()));
        }
        return views;
    }

    private RunView toView(ReconRun run) {
        var counterparty = counterparties.findById(run.getCounterpartyId()).orElse(null);
        return new RunView(run.getId(), run.getCounterpartyId(),
                counterparty == null ? "unknown" : counterparty.getName(),
                run.getBusinessDate(), run.getSequenceNo(), run.getStatus().name(), run.getRuleVersion(),
                run.getLedgerCount(), run.getStatementCount(), run.getMatchedCount(),
                run.getDiscrepancyCount(), run.matchRate(),
                run.getMatchedByPassA(), run.getMatchedByPassB(),
                run.getMatchedByPassC(), run.getMatchedByPassD(),
                run.getLedgerGrossMinorUnits(), run.getStatementGrossMinorUnits(),
                run.getDurationMillis(), counterparty == null ? "KRW" : counterparty.getCurrency());
    }
}
