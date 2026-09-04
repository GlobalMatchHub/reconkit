package dev.sellerkit.reconkit.app.web;

import dev.sellerkit.reconkit.app.repo.CounterpartyRepository;
import dev.sellerkit.reconkit.app.repo.DiscrepancyRepository;
import dev.sellerkit.reconkit.app.repo.LedgerEntryRepository;
import dev.sellerkit.reconkit.app.repo.StatementEntryRepository;
import dev.sellerkit.reconkit.app.service.DiscrepancyService;
import dev.sellerkit.reconkit.app.web.ReconController.EntryView;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyStatus;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyType;
import dev.sellerkit.reconkit.domain.enums.EntrySide;
import dev.sellerkit.reconkit.domain.enums.Severity;
import dev.sellerkit.reconkit.domain.model.Discrepancy;
import java.time.LocalDate;
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
@RequestMapping("/api/discrepancies")
public class DiscrepancyController {

    private final DiscrepancyRepository discrepancies;
    private final DiscrepancyService service;
    private final LedgerEntryRepository ledgerEntries;
    private final StatementEntryRepository statementEntries;
    private final CounterpartyRepository counterparties;

    public DiscrepancyController(DiscrepancyRepository discrepancies, DiscrepancyService service,
                                 LedgerEntryRepository ledgerEntries,
                                 StatementEntryRepository statementEntries,
                                 CounterpartyRepository counterparties) {
        this.discrepancies = discrepancies;
        this.service = service;
        this.ledgerEntries = ledgerEntries;
        this.statementEntries = statementEntries;
        this.counterparties = counterparties;
    }

    public record View(Long id, Long runId, Long counterpartyId, String counterpartyName,
                       LocalDate businessDate, String type, String severity, String status,
                       long deltaMinor, String currency, String detail, String assignee,
                       String resolutionNote, String resolvedBy, Long autoClosedByRunId, long version) {
    }

    public record Detail(View discrepancy, EntryView ledgerEntry, EntryView statementEntry) {
    }

    public record ResolveRequest(String status, String note, long version) {
    }

    public record Listing(List<View> items, long total, int page, int size) {
    }

    @GetMapping
    public Listing search(@RequestParam(required = false) DiscrepancyStatus status,
                          @RequestParam(required = false) Severity severity,
                          @RequestParam(required = false) DiscrepancyType type,
                          @RequestParam(required = false) Long counterpartyId,
                          @RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "50") int size) {
        LocalDate end = to != null ? to : LocalDate.now().plusYears(1);
        LocalDate start = from != null ? from : LocalDate.now().minusYears(5);
        var found = discrepancies.search(status, severity, type, counterpartyId, start, end,
                PageRequest.of(page, Math.min(size, 200)));
        return new Listing(found.getContent().stream().map(this::toView).toList(),
                found.getTotalElements(), page, size);
    }

    @GetMapping("/{id}")
    public Detail one(@PathVariable Long id) {
        Discrepancy discrepancy = discrepancies.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown difference " + id));
        EntryView ledger = discrepancy.getLedgerEntryId() == null ? null
                : ledgerEntries.findById(discrepancy.getLedgerEntryId())
                        .map(entry -> EntryView.of(entry, EntrySide.LEDGER)).orElse(null);
        EntryView statement = discrepancy.getStatementEntryId() == null ? null
                : statementEntries.findById(discrepancy.getStatementEntryId())
                        .map(entry -> EntryView.of(entry, EntrySide.STATEMENT)).orElse(null);
        return new Detail(toView(discrepancy), ledger, statement);
    }

    /**
     * The version the client read comes back with the decision. If the row has moved, the
     * update is refused with a conflict instead of overwriting somebody else's note.
     */
    @PostMapping("/{id}/resolve")
    public View resolve(@PathVariable Long id, @RequestBody ResolveRequest request) {
        return toView(service.resolve(id, DiscrepancyStatus.valueOf(request.status()),
                request.note(), request.version()));
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return Map.of(
                "types", DiscrepancyType.values(),
                "severities", Severity.values(),
                "statuses", DiscrepancyStatus.values());
    }

    private View toView(Discrepancy discrepancy) {
        String name = counterparties.findById(discrepancy.getCounterpartyId())
                .map(dev.sellerkit.reconkit.domain.model.Counterparty::getName).orElse("unknown");
        return new View(discrepancy.getId(), discrepancy.getRunId(), discrepancy.getCounterpartyId(), name,
                discrepancy.getBusinessDate(), discrepancy.getType().name(),
                discrepancy.getSeverity().name(), discrepancy.getStatus().name(),
                discrepancy.getDeltaMinorUnits(), discrepancy.getCurrency(), discrepancy.getDetail(),
                discrepancy.getAssignee(), discrepancy.getResolutionNote(), discrepancy.getResolvedBy(),
                discrepancy.getAutoClosedByRunId(), discrepancy.getVersion());
    }
}
