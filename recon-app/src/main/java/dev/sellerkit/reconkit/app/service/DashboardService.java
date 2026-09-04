package dev.sellerkit.reconkit.app.service;

import dev.sellerkit.reconkit.app.repo.CounterpartyRepository;
import dev.sellerkit.reconkit.app.repo.DiscrepancyRepository;
import dev.sellerkit.reconkit.app.repo.ReconRunRepository;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyStatus;
import dev.sellerkit.reconkit.domain.enums.DiscrepancyType;
import dev.sellerkit.reconkit.domain.enums.Severity;
import dev.sellerkit.reconkit.domain.model.Counterparty;
import dev.sellerkit.reconkit.domain.model.ReconRun;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbers the morning view is built from.
 *
 * <p>Only the latest run of each counterparty and date is counted. A date re-run three
 * times has three runs and one truth, and summing them would show a match rate that
 * improves every time somebody presses the button.
 */
@Service
public class DashboardService {

    private static final List<DiscrepancyStatus> OPEN_STATUSES =
            List.of(DiscrepancyStatus.OPEN, DiscrepancyStatus.INVESTIGATING);

    private final ReconRunRepository runs;
    private final DiscrepancyRepository discrepancies;
    private final CounterpartyRepository counterparties;

    public DashboardService(ReconRunRepository runs,
                            DiscrepancyRepository discrepancies,
                            CounterpartyRepository counterparties) {
        this.runs = runs;
        this.discrepancies = discrepancies;
        this.counterparties = counterparties;
    }

    public record DailyPoint(LocalDate date, int matched, int unmatched, int discrepancies, double matchRate) {
    }

    public record ChannelSummary(Long counterpartyId, String code, String name, String channelType,
                                 LocalDate latestDate, double matchRate, int openDiscrepancies,
                                 long ledgerGrossMinor, long statementGrossMinor, String currency) {
    }

    public record TypeCount(String type, long count, long absoluteAmountMinor) {
    }

    public record Summary(LocalDate from, LocalDate to,
                          int runCount, int ledgerRows, int statementRows,
                          int matchedRows, double matchRate,
                          long openDiscrepancies, long unexplainedMinor,
                          Map<String, Long> bySeverity,
                          List<TypeCount> byType,
                          List<DailyPoint> daily,
                          List<ChannelSummary> channels,
                          Map<String, Integer> byPass) {
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        List<ReconRun> allRuns = runs.findByBusinessDateBetweenOrderByBusinessDateDescIdDesc(from, to);
        Map<String, ReconRun> latest = new LinkedHashMap<>();
        for (ReconRun run : allRuns) {
            latest.putIfAbsent(run.getCounterpartyId() + "|" + run.getBusinessDate(), run);
        }
        List<ReconRun> effective = new ArrayList<>(latest.values());

        int ledgerRows = 0;
        int statementRows = 0;
        int matchedRows = 0;
        Map<LocalDate, int[]> perDay = new LinkedHashMap<>();
        Map<String, Integer> byPass = new LinkedHashMap<>();
        byPass.put("A_EXACT_ID", 0);
        byPass.put("B_COMPOSITE_KEY", 0);
        byPass.put("C_FUZZY_AMOUNT_TIME", 0);
        byPass.put("D_AGGREGATE", 0);

        for (ReconRun run : effective) {
            ledgerRows += run.getLedgerCount();
            statementRows += run.getStatementCount();
            matchedRows += run.getMatchedCount();
            int[] bucket = perDay.computeIfAbsent(run.getBusinessDate(), date -> new int[3]);
            bucket[0] += run.getMatchedCount();
            bucket[1] += run.getLedgerCount() + run.getStatementCount() - run.getMatchedCount();
            bucket[2] += run.getDiscrepancyCount();
            byPass.merge("A_EXACT_ID", run.getMatchedByPassA(), Integer::sum);
            byPass.merge("B_COMPOSITE_KEY", run.getMatchedByPassB(), Integer::sum);
            byPass.merge("C_FUZZY_AMOUNT_TIME", run.getMatchedByPassC(), Integer::sum);
            byPass.merge("D_AGGREGATE", run.getMatchedByPassD(), Integer::sum);
        }

        List<DailyPoint> daily = new ArrayList<>();
        perDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int matched = entry.getValue()[0];
                    int unmatched = entry.getValue()[1];
                    int total = matched + unmatched;
                    daily.add(new DailyPoint(entry.getKey(), matched, unmatched, entry.getValue()[2],
                            total == 0 ? 1.0d : (double) matched / total));
                });

        Map<String, Long> bySeverity = new EnumMap<>(Severity.class).isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            bySeverity.put(severity.name(), 0L);
        }
        for (Object[] row : discrepancies.countBySeverity(OPEN_STATUSES, from, to)) {
            bySeverity.put(((Severity) row[0]).name(), (Long) row[1]);
        }

        List<TypeCount> byType = new ArrayList<>();
        long unexplained = 0L;
        for (Object[] row : discrepancies.countByType(OPEN_STATUSES, from, to)) {
            long amount = ((Number) row[2]).longValue();
            byType.add(new TypeCount(((DiscrepancyType) row[0]).name(), (Long) row[1], amount));
            unexplained += amount;
        }

        List<ChannelSummary> channels = new ArrayList<>();
        for (Counterparty counterparty : counterparties.findAllByActiveTrueOrderByIdAsc()) {
            List<ReconRun> forChannel = effective.stream()
                    .filter(run -> run.getCounterpartyId().equals(counterparty.getId()))
                    .sorted(Comparator.comparing(ReconRun::getBusinessDate).reversed())
                    .toList();
            int channelMatched = forChannel.stream().mapToInt(ReconRun::getMatchedCount).sum();
            int channelTotal = forChannel.stream()
                    .mapToInt(run -> run.getLedgerCount() + run.getStatementCount()).sum();
            long open = discrepancies.countByCounterpartyIdAndBusinessDateBetweenAndStatusIn(
                    counterparty.getId(), from, to, OPEN_STATUSES);
            channels.add(new ChannelSummary(
                    counterparty.getId(), counterparty.getCode(), counterparty.getName(),
                    counterparty.getChannelType().name(),
                    forChannel.isEmpty() ? null : forChannel.get(0).getBusinessDate(),
                    channelTotal == 0 ? 1.0d : (double) channelMatched / channelTotal,
                    (int) open,
                    forChannel.stream().mapToLong(ReconRun::getLedgerGrossMinorUnits).sum(),
                    forChannel.stream().mapToLong(ReconRun::getStatementGrossMinorUnits).sum(),
                    counterparty.getCurrency()));
        }

        int totalRows = ledgerRows + statementRows;
        return new Summary(from, to, effective.size(), ledgerRows, statementRows, matchedRows,
                totalRows == 0 ? 1.0d : (double) matchedRows / totalRows,
                discrepancies.countByStatusIn(OPEN_STATUSES),
                unexplained, bySeverity, byType, daily, channels, byPass);
    }

    @Transactional(readOnly = true)
    public LocalDate latestBusinessDate() {
        return runs.findAllByOrderByIdDesc(org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst().map(ReconRun::getBusinessDate).orElse(LocalDate.now());
    }
}
