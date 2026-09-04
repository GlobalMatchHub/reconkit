package dev.sellerkit.reconkit.app.web;

import dev.sellerkit.reconkit.app.repo.CounterpartyRepository;
import dev.sellerkit.reconkit.app.repo.SettlementLineRepository;
import dev.sellerkit.reconkit.app.repo.SettlementRepository;
import dev.sellerkit.reconkit.app.service.SettlementService;
import dev.sellerkit.reconkit.domain.model.Settlement;
import dev.sellerkit.reconkit.domain.model.SettlementLine;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService service;
    private final SettlementRepository settlements;
    private final SettlementLineRepository lines;
    private final CounterpartyRepository counterparties;

    public SettlementController(SettlementService service, SettlementRepository settlements,
                                SettlementLineRepository lines, CounterpartyRepository counterparties) {
        this.service = service;
        this.settlements = settlements;
        this.lines = lines;
        this.counterparties = counterparties;
    }

    public record GenerateRequest(Long counterpartyId,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
    }

    public record LineView(LocalDate businessDate, int transactionCount, long grossMinor, long refundMinor,
                           long feeMinor, long feeVatMinor, long netMinor) {
    }

    public record View(Long id, Long counterpartyId, String counterpartyName, String statementNo,
                       LocalDate periodStart, LocalDate periodEnd, LocalDate payoutDate, String status,
                       String currency, long grossMinor, long refundMinor, long feeMinor, long feeVatMinor,
                       long adjustmentMinor, long holdbackMinor, long holdbackReleaseMinor,
                       long netPayableMinor, int transactionCount, int openDiscrepancyCount,
                       String confirmedBy, List<LineView> lines) {
    }

    @GetMapping
    public List<View> list() {
        return settlements.findAllByOrderByPeriodEndDescIdDesc().stream()
                .map(settlement -> toView(settlement, List.of())).toList();
    }

    @GetMapping("/{id}")
    public View one(@PathVariable Long id) {
        Settlement settlement = settlements.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown settlement " + id));
        return toView(settlement, lines.findBySettlementIdOrderByBusinessDateAsc(id));
    }

    @PostMapping
    public View generate(@RequestBody GenerateRequest request) {
        Settlement settlement = service.generate(
                request.counterpartyId(), request.periodStart(), request.periodEnd());
        return toView(settlement, lines.findBySettlementIdOrderByBusinessDateAsc(settlement.getId()));
    }

    @PostMapping("/{id}/confirm")
    public View confirm(@PathVariable Long id) {
        Settlement settlement = service.confirm(id);
        return toView(settlement, lines.findBySettlementIdOrderByBusinessDateAsc(id));
    }

    private View toView(Settlement settlement, List<SettlementLine> settlementLines) {
        String name = counterparties.findById(settlement.getCounterpartyId())
                .map(dev.sellerkit.reconkit.domain.model.Counterparty::getName).orElse("unknown");
        return new View(settlement.getId(), settlement.getCounterpartyId(), name, settlement.getStatementNo(),
                settlement.getPeriodStart(), settlement.getPeriodEnd(), settlement.getPayoutDate(),
                settlement.getStatus().name(), settlement.getCurrency(),
                settlement.getGrossMinorUnits(), settlement.getRefundMinorUnits(),
                settlement.getFeeMinorUnits(), settlement.getFeeVatMinorUnits(),
                settlement.getAdjustmentMinorUnits(), settlement.getHoldbackMinorUnits(),
                settlement.getHoldbackReleaseMinorUnits(), settlement.getNetPayableMinorUnits(),
                settlement.getTransactionCount(), settlement.getOpenDiscrepancyCount(),
                settlement.getConfirmedBy(),
                settlementLines.stream().map(line -> new LineView(
                        line.getBusinessDate(), line.getTransactionCount(), line.getGrossMinorUnits(),
                        line.getRefundMinorUnits(), line.getFeeMinorUnits(), line.getFeeVatMinorUnits(),
                        line.getNetMinorUnits())).toList());
    }
}
