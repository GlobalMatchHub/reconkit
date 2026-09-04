package dev.sellerkit.reconkit.app.web;

import dev.sellerkit.reconkit.app.repo.CounterpartyRepository;
import dev.sellerkit.reconkit.app.repo.IngestBatchRepository;
import dev.sellerkit.reconkit.app.repo.TenantRepository;
import dev.sellerkit.reconkit.app.service.IngestService;
import dev.sellerkit.reconkit.app.tenancy.TenantContext;
import dev.sellerkit.reconkit.domain.enums.EntrySide;
import dev.sellerkit.reconkit.domain.model.IngestBatch;
import dev.sellerkit.reconkit.ingest.MappingProfiles;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final IngestService service;
    private final IngestBatchRepository batches;
    private final CounterpartyRepository counterparties;
    private final TenantRepository tenants;

    public IngestController(IngestService service, IngestBatchRepository batches,
                            CounterpartyRepository counterparties, TenantRepository tenants) {
        this.service = service;
        this.batches = batches;
        this.counterparties = counterparties;
        this.tenants = tenants;
    }

    public record BatchView(Long id, Long counterpartyId, String counterpartyName, String side,
                            String sourceName, String contentHash, LocalDate businessDate, String status,
                            int rowCount, int rejectedCount, String rejectReason, Instant loadedAt) {
    }

    public record UploadResponse(Long batchId, boolean duplicate, int loaded, int rejected,
                                 List<String> rejectionSamples) {
    }

    @PostMapping
    public UploadResponse upload(@RequestParam Long counterpartyId,
                                 @RequestParam EntrySide side,
                                 @RequestParam MultipartFile file) throws IOException {
        ZoneId zone = tenants.findById(TenantContext.require())
                .map(tenant -> ZoneId.of(tenant.getTimeZone()))
                .orElse(ZoneId.of("Asia/Seoul"));
        IngestService.Outcome outcome = service.load(
                counterpartyId, side, file.getOriginalFilename(), file.getBytes(), zone);
        return new UploadResponse(outcome.batchId(), outcome.duplicate(), outcome.loaded(),
                outcome.rejected(),
                outcome.rejections().stream().limit(10)
                        .map(row -> "line " + row.lineNumber() + ": " + row.reason()).toList());
    }

    @GetMapping("/batches")
    public List<BatchView> batches() {
        return batches.findTop50ByOrderByIdDesc().stream().map(this::toView).toList();
    }

    @GetMapping("/profiles")
    public Set<String> profiles() {
        return MappingProfiles.all().keySet();
    }

    private BatchView toView(IngestBatch batch) {
        String name = counterparties.findById(batch.getCounterpartyId())
                .map(dev.sellerkit.reconkit.domain.model.Counterparty::getName).orElse("unknown");
        return new BatchView(batch.getId(), batch.getCounterpartyId(), name, batch.getSide().name(),
                batch.getSourceName(), batch.getContentHash(), batch.getBusinessDate(),
                batch.getStatus().name(), batch.getRowCount(), batch.getRejectedCount(),
                batch.getRejectReason(), batch.getLoadedAt());
    }
}
