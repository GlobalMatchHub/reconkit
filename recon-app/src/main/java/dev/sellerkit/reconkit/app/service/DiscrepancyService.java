package dev.sellerkit.reconkit.app.service;

import dev.sellerkit.reconkit.app.audit.AuditService;
import dev.sellerkit.reconkit.app.repo.DiscrepancyRepository;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyStatus;
import dev.sellerkit.reconkit.domain.model.Discrepancy;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscrepancyService {

    private final DiscrepancyRepository discrepancies;
    private final AuditService audit;

    public DiscrepancyService(DiscrepancyRepository discrepancies, AuditService audit) {
        this.discrepancies = discrepancies;
        this.audit = audit;
    }

    /**
     * Records an operator's decision on one difference.
     *
     * <p>The caller sends the version it read. If the row has moved since, the update is
     * rejected with a conflict rather than overwriting. Two people work the same morning
     * queue, and without this the second save quietly discards the first one's note, which
     * is a failure nobody sees until the counterparty asks why the explanation changed.
     */
    @Transactional
    public Discrepancy resolve(Long id, DiscrepancyStatus outcome, String note, long expectedVersion) {
        Discrepancy discrepancy = discrepancies.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown difference " + id));
        if (discrepancy.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(Discrepancy.class, id);
        }
        Map<String, Object> before = Map.of(
                "status", discrepancy.getStatus().name(),
                "note", String.valueOf(discrepancy.getResolutionNote()));

        discrepancy.resolve(outcome, note, AuditService.currentActor());
        discrepancies.save(discrepancy);

        audit.record("DISCREPANCY_RESOLVE", "Discrepancy", id, before,
                Map.of("status", outcome.name(), "note", String.valueOf(note)));
        return discrepancy;
    }
}
