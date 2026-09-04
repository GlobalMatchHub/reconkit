package dev.sellerkit.reconkit.app.service;

import dev.sellerkit.reconkit.app.audit.AuditService;
import dev.sellerkit.reconkit.app.repo.CounterpartyRepository;
import dev.sellerkit.reconkit.app.repo.IngestBatchRepository;
import dev.sellerkit.reconkit.app.repo.LedgerEntryRepository;
import dev.sellerkit.reconkit.app.repo.StatementEntryRepository;
import dev.sellerkit.reconkit.domain.enums.EntrySide;
import dev.sellerkit.reconkit.domain.model.Counterparty;
import dev.sellerkit.reconkit.domain.model.IngestBatch;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import dev.sellerkit.reconkit.domain.model.TransactionEntry;
import dev.sellerkit.reconkit.domain.policy.BusinessDateResolver;
import dev.sellerkit.reconkit.ingest.CsvIngestor;
import dev.sellerkit.reconkit.ingest.IngestResult;
import dev.sellerkit.reconkit.ingest.MappingProfile;
import dev.sellerkit.reconkit.ingest.MappingProfiles;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final CsvIngestor ingestor = new CsvIngestor();
    private final CounterpartyRepository counterparties;
    private final IngestBatchRepository batches;
    private final LedgerEntryRepository ledgerEntries;
    private final StatementEntryRepository statementEntries;
    private final AuditService audit;

    public IngestService(CounterpartyRepository counterparties,
                         IngestBatchRepository batches,
                         LedgerEntryRepository ledgerEntries,
                         StatementEntryRepository statementEntries,
                         AuditService audit) {
        this.counterparties = counterparties;
        this.batches = batches;
        this.ledgerEntries = ledgerEntries;
        this.statementEntries = statementEntries;
        this.audit = audit;
    }

    public record Outcome(Long batchId, boolean duplicate, int loaded, int rejected,
                          List<IngestResult.RejectedRow> rejections) {
    }

    /**
     * Loads one file.
     *
     * <p>The hash check comes first and short circuits the whole operation. Re-sending a
     * settlement file is a normal part of operations, and the second load is not an error
     * to shout about, it is a no op that should say so plainly and point at the batch that
     * already holds the data.
     */
    @Transactional
    public Outcome load(Long counterpartyId, EntrySide side, String sourceName,
                        byte[] content, ZoneId zone) {
        Counterparty counterparty = counterparties.findById(counterpartyId)
                .orElseThrow(() -> new IllegalArgumentException("unknown counterparty " + counterpartyId));

        String hash = CsvIngestor.sha256(content);
        var existing = batches.findByCounterpartyIdAndContentHash(counterpartyId, hash);
        if (existing.isPresent()) {
            log.info("file {} for counterparty {} was already loaded as batch {}",
                    sourceName, counterpartyId, existing.get().getId());
            return new Outcome(existing.get().getId(), true,
                    existing.get().getRowCount(), existing.get().getRejectedCount(), List.of());
        }

        MappingProfile profile = side == EntrySide.LEDGER
                ? MappingProfiles.INTERNAL_LEDGER
                : MappingProfiles.require(counterparty.getMappingProfile());
        BusinessDateResolver resolver =
                new BusinessDateResolver(zone, counterparty.getTerms().getCutoffTime());

        IngestResult result = ingestor.ingest(content, profile, side, counterpartyId, resolver);
        LocalDate businessDate = result.entries().stream()
                .map(TransactionEntry::getBusinessDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now(zone));

        IngestBatch batch = batches.save(
                new IngestBatch(counterpartyId, side, sourceName, hash, businessDate));

        List<LedgerEntry> ledgerRows = new ArrayList<>();
        List<StatementEntry> statementRows = new ArrayList<>();
        for (TransactionEntry entry : result.entries()) {
            entry.setBatchId(batch.getId());
            if (entry instanceof LedgerEntry row) {
                ledgerRows.add(row);
            } else {
                statementRows.add((StatementEntry) entry);
            }
        }
        if (!ledgerRows.isEmpty()) {
            ledgerEntries.saveAll(ledgerRows);
        }
        if (!statementRows.isEmpty()) {
            statementEntries.saveAll(statementRows);
        }

        batch.markLoaded(result.loadedCount(), result.rejectedCount());
        batches.save(batch);

        audit.record("INGEST_LOAD", "IngestBatch", batch.getId(), Map.of(
                "source", sourceName,
                "side", side.name(),
                "rows", result.loadedCount(),
                "rejected", result.rejectedCount(),
                "hash", hash));

        return new Outcome(batch.getId(), false, result.loadedCount(), result.rejectedCount(), result.rejected());
    }
}
