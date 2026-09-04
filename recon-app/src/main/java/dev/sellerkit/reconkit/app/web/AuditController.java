package dev.sellerkit.reconkit.app.web;

import dev.sellerkit.reconkit.app.repo.AuditLogRepository;
import dev.sellerkit.reconkit.domain.model.AuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository auditLogs;

    public AuditController(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    public record View(Long id, String actor, String action, String entityType, String entityId,
                       String beforeJson, String afterJson, String requestId, Instant occurredAt) {
    }

    public record Listing(List<View> items, long total, int page, int size) {
    }

    @GetMapping
    public Listing list(@RequestParam(required = false) String entityType,
                        @RequestParam(required = false) String entityId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 200));
        var found = (entityType != null && entityId != null)
                ? auditLogs.findByEntityTypeAndEntityIdOrderByIdDesc(entityType, entityId, pageable)
                : auditLogs.findAllByOrderByIdDesc(pageable);
        return new Listing(found.getContent().stream().map(this::toView).toList(),
                found.getTotalElements(), page, size);
    }

    private View toView(AuditLog log) {
        return new View(log.getId(), log.getActor(), log.getAction(), log.getEntityType(),
                log.getEntityId(), log.getBeforeJson(), log.getAfterJson(),
                log.getRequestId(), log.getOccurredAt());
    }
}
