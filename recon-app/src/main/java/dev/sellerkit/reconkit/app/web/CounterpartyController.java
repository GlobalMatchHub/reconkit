package dev.sellerkit.reconkit.app.web;

import dev.sellerkit.reconkit.app.repo.CounterpartyRepository;
import dev.sellerkit.reconkit.domain.model.Counterparty;
import java.time.LocalTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/counterparties")
public class CounterpartyController {

    private final CounterpartyRepository counterparties;

    public CounterpartyController(CounterpartyRepository counterparties) {
        this.counterparties = counterparties;
    }

    public record View(Long id, String code, String name, String channelType, String currency,
                       String mappingProfile, int feeRateBasisPoints, long fixedFeeMinor,
                       int feeVatBasisPoints, String cycle, int settleAfterDays,
                       int holdbackBasisPoints, LocalTime cutoffTime,
                       long amountToleranceAbsolute, int amountToleranceBasisPoints,
                       long feeToleranceAbsolute, int feeToleranceBasisPoints, int matchWindowDays) {

        static View of(Counterparty counterparty) {
            var terms = counterparty.getTerms();
            return new View(counterparty.getId(), counterparty.getCode(), counterparty.getName(),
                    counterparty.getChannelType().name(), counterparty.getCurrency(),
                    counterparty.getMappingProfile(), terms.getFeeRateBasisPoints(),
                    terms.getFixedFeeMinorUnits(), terms.getFeeVatBasisPoints(),
                    terms.getCycle().name(), terms.getSettleAfterDays(),
                    terms.getHoldbackBasisPoints(), terms.getCutoffTime(),
                    terms.getAmountToleranceAbsolute(), terms.getAmountToleranceBasisPoints(),
                    terms.getFeeToleranceAbsolute(), terms.getFeeToleranceBasisPoints(),
                    terms.getMatchWindowDays());
        }
    }

    @GetMapping
    public List<View> list() {
        return counterparties.findAllByActiveTrueOrderByIdAsc().stream().map(View::of).toList();
    }
}
