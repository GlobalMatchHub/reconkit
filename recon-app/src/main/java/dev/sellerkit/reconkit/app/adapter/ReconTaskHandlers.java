package dev.sellerkit.reconkit.app.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sellerkit.reconkit.app.repo.SettlementRepository;
import dev.sellerkit.reconkit.app.service.ReconService;
import dev.sellerkit.reconkit.app.service.SettlementService;
import dev.sellerkit.reconkit.domain.enums.SettlementStatus;
import dev.sellerkit.reconkit.domain.model.ReprocessTask;
import dev.sellerkit.reconkit.reprocess.IrreversibleTaskException;
import dev.sellerkit.reconkit.reprocess.TaskHandler;
import dev.sellerkit.reconkit.reprocess.TaskHandlerRegistry;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The work a reprocess job can be made of.
 *
 * <p>Note which of these declares itself irreversible. Re-running reconciliation writes a
 * new run and can be rolled back by voiding it. Confirming a settlement moves money in the
 * real world, and the honest compensation for that is to say so and stop, not to attempt a
 * reversal the payment rail may not accept.
 */
@Configuration
public class ReconTaskHandlers {

    private static final Logger log = LoggerFactory.getLogger(ReconTaskHandlers.class);

    @Bean
    TaskHandlerRegistry taskHandlerRegistry(ReconService reconService,
                                            SettlementService settlementService,
                                            SettlementRepository settlements,
                                            ObjectMapper objectMapper) {
        return new TaskHandlerRegistry()
                .register(rerunRecon(reconService, objectMapper))
                .register(regenerateSettlement(settlementService, settlements, objectMapper))
                .register(confirmSettlement(settlementService, objectMapper));
    }

    private TaskHandler rerunRecon(ReconService reconService, ObjectMapper objectMapper) {
        return new TaskHandler() {
            @Override
            public String action() {
                return "RERUN_RECON";
            }

            @Override
            public void execute(ReprocessTask task) {
                JsonNode payload = read(objectMapper, task.getPayloadJson());
                reconService.run(payload.get("counterpartyId").asLong(),
                        LocalDate.parse(payload.get("businessDate").asText()));
            }

            @Override
            public void compensate(ReprocessTask task) {
                // A run is additive and immutable. Undoing it means leaving the previous
                // run as the current answer, which is what already happens: nothing to do.
                log.info("rerun of {} left in place, the earlier run remains the reported one",
                        task.getPayloadJson());
            }
        };
    }

    private TaskHandler regenerateSettlement(SettlementService settlementService,
                                             SettlementRepository settlements,
                                             ObjectMapper objectMapper) {
        return new TaskHandler() {
            @Override
            public String action() {
                return "REGENERATE_SETTLEMENT";
            }

            @Override
            public void execute(ReprocessTask task) {
                JsonNode payload = read(objectMapper, task.getPayloadJson());
                settlementService.generate(
                        payload.get("counterpartyId").asLong(),
                        LocalDate.parse(payload.get("periodStart").asText()),
                        LocalDate.parse(payload.get("periodEnd").asText()));
            }

            @Override
            public void compensate(ReprocessTask task) {
                JsonNode compensation = read(objectMapper, task.getCompensationJson());
                Long settlementId = compensation.get("settlementId").asLong();
                settlements.findById(settlementId).ifPresent(settlement -> {
                    if (settlement.getStatus() == SettlementStatus.PAID) {
                        throw new IrreversibleTaskException(
                                "settlement " + settlementId + " has already been paid");
                    }
                    settlement.voidStatement();
                    settlements.save(settlement);
                });
            }
        };
    }

    private TaskHandler confirmSettlement(SettlementService settlementService, ObjectMapper objectMapper) {
        return new TaskHandler() {
            @Override
            public String action() {
                return "CONFIRM_SETTLEMENT";
            }

            @Override
            public void execute(ReprocessTask task) {
                JsonNode payload = read(objectMapper, task.getPayloadJson());
                settlementService.confirm(payload.get("settlementId").asLong());
            }

            @Override
            public void compensate(ReprocessTask task) {
                throw new IrreversibleTaskException(
                        "a confirmed settlement has been handed to payments and cannot be undone here");
            }
        };
    }

    private static JsonNode read(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("task payload is not readable: " + json, ex);
        }
    }
}
