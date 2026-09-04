package dev.sellerkit.reconkit.app.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sellerkit.reconkit.app.repo.AuditLogRepository;
import dev.sellerkit.reconkit.app.security.RequestIdFilter;
import dev.sellerkit.reconkit.domain.model.AuditLog;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail.
 *
 * <p>Runs in the caller's transaction rather than its own. That is a deliberate trade:
 * a rolled back change leaves no audit row, which is correct, because a trail that
 * records attempts that never happened is worse than one that records only what did.
 * The consequence is that audit writing must never be the thing that fails a business
 * operation, so serialisation problems are swallowed and logged rather than thrown.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Object entityId, Object before, Object after) {
        repository.save(new AuditLog(
                currentActor(),
                action,
                entityType,
                entityId == null ? null : String.valueOf(entityId),
                toJson(before),
                toJson(after),
                RequestIdFilter.current()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Object entityId, Map<String, ?> after) {
        record(action, entityType, entityId, null, after);
    }

    public static String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : String.valueOf(authentication.getPrincipal());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"unserialisable\":\"" + value.getClass().getSimpleName() + "\"}";
        }
    }
}
