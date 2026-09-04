package dev.sellerkit.reconkit.app.demo;

import dev.sellerkit.reconkit.app.repo.AppUserRepository;
import dev.sellerkit.reconkit.app.repo.CounterpartyRepository;
import dev.sellerkit.reconkit.app.repo.IngestBatchRepository;
import dev.sellerkit.reconkit.app.repo.LedgerEntryRepository;
import dev.sellerkit.reconkit.app.repo.StatementEntryRepository;
import dev.sellerkit.reconkit.app.repo.TenantRepository;
import dev.sellerkit.reconkit.app.service.ReconService;
import dev.sellerkit.reconkit.app.service.SettlementService;
import dev.sellerkit.reconkit.app.tenancy.TenantContext;
import dev.sellerkit.reconkit.domain.enums.ChannelType;
import dev.sellerkit.reconkit.domain.enums.EntrySide;
import dev.sellerkit.reconkit.domain.enums.SettlementCycle;
import dev.sellerkit.reconkit.domain.enums.TxnStatus;
import dev.sellerkit.reconkit.domain.enums.UserRole;
import dev.sellerkit.reconkit.domain.model.AppUser;
import dev.sellerkit.reconkit.domain.model.Counterparty;
import dev.sellerkit.reconkit.domain.model.IngestBatch;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import dev.sellerkit.reconkit.domain.model.Tenant;
import dev.sellerkit.reconkit.domain.model.TransactionEntry;
import dev.sellerkit.reconkit.domain.policy.FeeCalculator;
import dev.sellerkit.reconkit.domain.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Builds a month of believable traffic with known defects planted in it.
 *
 * <p>Demo data that reconciles perfectly proves nothing, and demo data that is random
 * noise proves less. Every defect here is one that turns up in real settlement work, and
 * each is planted a known number of times so the dashboard can be checked against the
 * generator rather than admired.
 *
 * <p>The seeder is off unless {@code reconkit.demo.enabled} is set. It writes directly to
 * the tables rather than through the upload endpoint, because generating thirty days of
 * CSV and posting it back would test the multipart parser, not the engine. One day of each
 * counterparty's file is written to disk anyway, so the upload path has something real to
 * demonstrate.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String TENANT = "acme";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // Planted defect budget for the whole month, spent by the generator below.
    private static final int MISSING_IN_STATEMENT = 12;
    private static final int MISSING_IN_LEDGER = 5;
    private static final int DUPLICATED_STATEMENT_LINES = 3;
    private static final int AMOUNT_MISMATCHES = 8;
    private static final int FEE_MISMATCHES = 21;
    private static final int LATE_POSTINGS = 40;
    private static final int STATUS_MISMATCHES = 6;

    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final CounterpartyRepository counterparties;
    private final IngestBatchRepository batches;
    private final LedgerEntryRepository ledgerEntries;
    private final StatementEntryRepository statementEntries;
    private final ReconService reconService;
    private final SettlementService settlementService;
    private final PasswordEncoder passwordEncoder;

    @Value("${reconkit.demo.enabled:false}")
    private boolean enabled;

    @Value("${reconkit.demo.days:30}")
    private int days;

    public DemoDataSeeder(TenantRepository tenants, AppUserRepository users,
                          CounterpartyRepository counterparties, IngestBatchRepository batches,
                          LedgerEntryRepository ledgerEntries, StatementEntryRepository statementEntries,
                          ReconService reconService, SettlementService settlementService,
                          PasswordEncoder passwordEncoder) {
        this.tenants = tenants;
        this.users = users;
        this.counterparties = counterparties;
        this.batches = batches;
        this.ledgerEntries = ledgerEntries;
        this.statementEntries = statementEntries;
        this.reconService = reconService;
        this.settlementService = settlementService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        TenantContext.runAs(TENANT, () -> {
            if (counterparties.findByCode("KPAY").isPresent()) {
                log.info("demo data is already present, leaving it alone");
                return null;
            }
            seed();
            return null;
        });
    }

    /**
     * Not wrapped in a single transaction. Thirty days of inserts followed by ninety
     * reconciliation runs in one transaction would hold locks for the whole seed and roll
     * the lot back on the last statement. Each run is already transactional on its own,
     * which is also how the system behaves in production.
     */
    private void seed() {
        long startedAt = System.currentTimeMillis();
        tenants.save(new Tenant(TENANT, "아크메커머스", "Asia/Seoul"));
        users.save(new AppUser(TENANT, "admin@reconkit.dev", "관리자",
                passwordEncoder.encode("reconkit"), UserRole.ADMIN));
        users.save(new AppUser(TENANT, "operator@reconkit.dev", "정산담당",
                passwordEncoder.encode("reconkit"), UserRole.OPERATOR));

        Counterparty gateway = counterparties.save(gateway());
        Counterparty marketplace = counterparties.save(marketplace());
        Counterparty appStore = counterparties.save(appStore());

        LocalDate today = LocalDate.now(SEOUL);
        LocalDate start = today.minusDays(days);
        Random random = new Random(20260904L);

        Defects defects = new Defects(random, days);

        for (int offset = 0; offset < days; offset++) {
            LocalDate date = start.plusDays(offset);
            generateGatewayDay(gateway, date, offset, random, defects);
            generateMarketplaceDay(marketplace, date, random);
            generateAppStoreDay(appStore, date, random);
        }

        log.info("demo transactions written in {} ms, reconciling",
                System.currentTimeMillis() - startedAt);

        for (int offset = 0; offset < days; offset++) {
            LocalDate date = start.plusDays(offset);
            for (Counterparty counterparty : List.of(gateway, marketplace, appStore)) {
                reconService.run(counterparty.getId(), date);
            }
        }

        // One settlement per counterparty, on the cycle each one actually uses.
        settlementService.generate(gateway.getId(), today.minusDays(7), today.minusDays(1));
        settlementService.generate(marketplace.getId(), today.minusDays(14), today.minusDays(8));
        settlementService.generate(appStore.getId(), start, today.minusDays(1));

        log.info("demo data ready in {} ms: {} days across three counterparties",
                System.currentTimeMillis() - startedAt, days);
    }

    private Counterparty gateway() {
        Counterparty counterparty = new Counterparty(
                "KPAY", "케이페이 결제대행", ChannelType.PAYMENT_GATEWAY, "KRW", "gateway-daily");
        SettlementTerms terms = new SettlementTerms();
        terms.setFeeRateBasisPoints(220);
        terms.setFeeVatBasisPoints(1000);
        terms.setCycle(SettlementCycle.DAILY_T_PLUS_N);
        terms.setSettleAfterDays(2);
        // The half hour that produces most of the month's late postings.
        terms.setCutoffTime(LocalTime.of(23, 30));
        terms.setAmountToleranceAbsolute(0L);
        terms.setFeeToleranceAbsolute(1L);
        terms.setFeeToleranceBasisPoints(10);
        terms.setMatchWindowDays(2);
        counterparty.setTerms(terms);
        return counterparty;
    }

    private Counterparty marketplace() {
        Counterparty counterparty = new Counterparty(
                "OMKT", "오픈마켓 파트너스", ChannelType.OPEN_MARKET, "KRW", "marketplace-payout");
        SettlementTerms terms = new SettlementTerms();
        terms.setFeeRateBasisPoints(1_200);
        terms.setFeeVatBasisPoints(1_000);
        terms.setCycle(SettlementCycle.WEEKLY);
        terms.setSettleAfterDays(3);
        terms.setHoldbackBasisPoints(300);
        terms.setHoldbackReleaseDays(30);
        terms.setAmountToleranceAbsolute(1L);
        terms.setFeeToleranceAbsolute(2L);
        terms.setFeeToleranceBasisPoints(20);
        terms.setMatchWindowDays(3);
        counterparty.setTerms(terms);
        return counterparty;
    }

    private Counterparty appStore() {
        Counterparty counterparty = new Counterparty(
                "APST", "앱스토어 인앱결제", ChannelType.APP_STORE, "KRW", "gateway-daily");
        SettlementTerms terms = new SettlementTerms();
        terms.setFeeRateBasisPoints(3_000);
        terms.setFeeVatBasisPoints(0);
        terms.setCycle(SettlementCycle.MONTHLY);
        terms.setSettleAfterDays(5);
        terms.setCutoffTime(LocalTime.of(0, 0));
        terms.setAmountToleranceAbsolute(0L);
        terms.setFeeToleranceAbsolute(1L);
        terms.setFeeToleranceBasisPoints(5);
        terms.setMatchWindowDays(2);
        counterparty.setTerms(terms);
        return counterparty;
    }

    /** Spreads the month's defect budget over the days, deterministically. */
    private static final class Defects {

        private final List<Integer> missingStatement = new ArrayList<>();
        private final List<Integer> missingLedger = new ArrayList<>();
        private final List<Integer> duplicates = new ArrayList<>();
        private final List<Integer> amountMismatch = new ArrayList<>();
        private final List<Integer> feeMismatch = new ArrayList<>();
        private final List<Integer> latePosting = new ArrayList<>();
        private final List<Integer> statusMismatch = new ArrayList<>();

        Defects(Random random, int days) {
            fill(missingStatement, MISSING_IN_STATEMENT, random, days);
            fill(missingLedger, MISSING_IN_LEDGER, random, days);
            fill(duplicates, DUPLICATED_STATEMENT_LINES, random, days);
            fill(amountMismatch, AMOUNT_MISMATCHES, random, days);
            fill(feeMismatch, FEE_MISMATCHES, random, days);
            fill(latePosting, LATE_POSTINGS, random, days);
            fill(statusMismatch, STATUS_MISMATCHES, random, days);
        }

        private static void fill(List<Integer> target, int count, Random random, int days) {
            for (int index = 0; index < count; index++) {
                target.add(random.nextInt(days));
            }
        }

        int onDay(List<Integer> budget, int day) {
            return (int) budget.stream().filter(value -> value == day).count();
        }
    }

    private void generateGatewayDay(Counterparty counterparty, LocalDate date, int dayIndex,
                                    Random random, Defects defects) {
        int day = (int) (date.toEpochDay() % 100_000);
        int volume = 420 + random.nextInt(180);

        List<LedgerEntry> ledger = new ArrayList<>(volume);
        List<StatementEntry> statement = new ArrayList<>(volume);

        int missing = defects.onDay(defects.missingStatement, dayIndex);
        int duplicated = defects.onDay(defects.duplicates, dayIndex);
        int amountOff = defects.onDay(defects.amountMismatch, dayIndex);
        int feeOff = defects.onDay(defects.feeMismatch, dayIndex);
        int late = defects.onDay(defects.latePosting, dayIndex);
        int orphan = defects.onDay(defects.missingLedger, dayIndex);
        int statusOff = defects.onDay(defects.statusMismatch, dayIndex);

        for (int index = 0; index < volume; index++) {
            String txnId = "KPAY%s%05d".formatted(date.toString().replace("-", ""), index);
            long amount = amountFor(random);
            boolean refund = random.nextInt(100) < 4;
            TxnStatus status = refund ? TxnStatus.REFUND : TxnStatus.PAID;
            // A fifth of the volume lands in the half hour before the counterparty's cutoff.
            int hour = random.nextInt(100) < 20 ? 23 : random.nextInt(23);
            int minute = hour == 23 ? 30 + random.nextInt(30) : random.nextInt(60);
            Instant occurredAt = date.atTime(hour, minute, random.nextInt(60)).atZone(SEOUL).toInstant();

            LedgerEntry ledgerRow = new LedgerEntry();
            fill(ledgerRow, counterparty, txnId, "A" + day + index, "ORD" + day + index,
                    occurredAt, date, amount, 0L, status, "CARD", "45" + (1000 + random.nextInt(8999)));
            ledger.add(ledgerRow);

            if (missing > 0 && index % 97 == 3) {
                missing--;
                continue;
            }

            StatementEntry statementRow = new StatementEntry();
            // A refund gives the fee back too, so the withheld amount is negative. Getting
            // this sign wrong makes every refund in the month look like a fee dispute.
            long signedGross = refund ? -amount : amount;
            long fee = FeeCalculator.totalFee(Money.krw(signedGross), counterparty.getTerms()).minorUnits();
            LocalDate statementDate = date;
            if (late > 0 && hour == 23 && minute >= 30) {
                late--;
                statementDate = date.plusDays(1);
            }
            long statementAmount = amount;
            if (amountOff > 0 && index % 89 == 7) {
                amountOff--;
                // Half the planted amount differences are small enough to look like
                // rounding and half are not, so the severity rules have something to sort.
                statementAmount = amount - (index % 2 == 0 ? 1_000L : 47_000L);
            }
            TxnStatus statementStatus = status;
            if (statusOff > 0 && index % 73 == 5) {
                statusOff--;
                statementStatus = TxnStatus.CANCEL;
            }
            if (feeOff > 0 && index % 61 == 11) {
                feeOff--;
                fee += 300L + random.nextInt(2_000);
            }
            fill(statementRow, counterparty, txnId, ledgerRow.getApprovalNo(), ledgerRow.getOrderId(),
                    occurredAt, statementDate, statementAmount, fee, statementStatus, "CARD",
                    ledgerRow.getCardBin());
            statement.add(statementRow);

            if (duplicated > 0 && index % 131 == 17) {
                duplicated--;
                StatementEntry copy = new StatementEntry();
                fill(copy, counterparty, txnId, ledgerRow.getApprovalNo(), ledgerRow.getOrderId(),
                        occurredAt, statementDate, statementAmount, fee, status, "CARD", ledgerRow.getCardBin());
                statement.add(copy);
            }
        }

        for (int index = 0; index < orphan; index++) {
            StatementEntry orphanRow = new StatementEntry();
            long amount = amountFor(random);
            fill(orphanRow, counterparty, "KPAYORPH%s%d".formatted(date, index),
                    "AORPH" + day + index, null,
                    date.atTime(12, index).atZone(SEOUL).toInstant(), date, amount,
                    FeeCalculator.totalFee(Money.krw(amount), counterparty.getTerms()).minorUnits(),
                    TxnStatus.PAID, "CARD", "451234");
            statement.add(orphanRow);
        }

        persist(counterparty, date, ledger, statement, "kpay_daily_%s.csv".formatted(date));
    }

    /**
     * The marketplace reports payouts, not transactions. Six orders share a settlement
     * number and the file carries one line for the batch, which is the shape pass D exists
     * for and which a one to one matcher reports as a hundred percent failure.
     */
    private void generateMarketplaceDay(Counterparty counterparty, LocalDate date, Random random) {
        int batchCount = 14 + random.nextInt(8);
        List<LedgerEntry> ledger = new ArrayList<>();
        List<StatementEntry> statement = new ArrayList<>();

        for (int batch = 0; batch < batchCount; batch++) {
            String settlementNo = "OM%s%03d".formatted(date.toString().replace("-", ""), batch);
            int size = 3 + random.nextInt(5);
            long total = 0L;
            for (int item = 0; item < size; item++) {
                long amount = amountFor(random);
                total += amount;
                LedgerEntry row = new LedgerEntry();
                fill(row, counterparty, null, null, settlementNo,
                        date.atTime(random.nextInt(22), random.nextInt(60)).atZone(SEOUL).toInstant(),
                        date, amount, 0L, TxnStatus.PAID, "MARKET", null);
                ledger.add(row);
            }
            StatementEntry payout = new StatementEntry();
            fill(payout, counterparty, null, null, settlementNo,
                    date.atTime(23, 0).atZone(SEOUL).toInstant(), date, total,
                    FeeCalculator.totalFee(Money.krw(total), counterparty.getTerms()).minorUnits(),
                    TxnStatus.PAID, "MARKET", null);
            statement.add(payout);
        }
        persist(counterparty, date, ledger, statement, "openmarket_payout_%s.csv".formatted(date));
    }

    /**
     * The app store reissues its transaction identifiers, so a slice of the file matches
     * on nothing but the approval number and the amount. That is pass B doing its job.
     */
    private void generateAppStoreDay(Counterparty counterparty, LocalDate date, Random random) {
        int volume = 140 + random.nextInt(80);
        int day = (int) (date.toEpochDay() % 100_000);
        List<LedgerEntry> ledger = new ArrayList<>(volume);
        List<StatementEntry> statement = new ArrayList<>(volume);

        for (int index = 0; index < volume; index++) {
            String txnId = "APST%s%05d".formatted(date.toString().replace("-", ""), index);
            long amount = 1_100L * (1 + random.nextInt(30));
            Instant occurredAt = date.atTime(random.nextInt(24), random.nextInt(60)).atZone(SEOUL).toInstant();

            LedgerEntry ledgerRow = new LedgerEntry();
            fill(ledgerRow, counterparty, txnId, "IAP" + day + index, "IAPORD" + day + index,
                    occurredAt, date, amount, 0L, TxnStatus.PAID, "IAP", null);
            ledger.add(ledgerRow);

            StatementEntry statementRow = new StatementEntry();
            // One in eight lines comes back with a regenerated identifier.
            String statementTxnId = index % 8 == 0 ? "REISSUE-" + txnId : txnId;
            fill(statementRow, counterparty, statementTxnId, ledgerRow.getApprovalNo(),
                    ledgerRow.getOrderId(), occurredAt, date, amount,
                    FeeCalculator.totalFee(Money.krw(amount), counterparty.getTerms()).minorUnits(),
                    TxnStatus.PAID, "IAP", null);
            statement.add(statementRow);
        }
        persist(counterparty, date, ledger, statement, "appstore_%s.csv".formatted(date));
    }

    private void persist(Counterparty counterparty, LocalDate date,
                         List<LedgerEntry> ledger, List<StatementEntry> statement, String sourceName) {
        IngestBatch ledgerBatch = batches.save(new IngestBatch(counterparty.getId(), EntrySide.LEDGER,
                "internal_export_%s.csv".formatted(date), hash(counterparty, date, "L"), date));
        IngestBatch statementBatch = batches.save(new IngestBatch(counterparty.getId(), EntrySide.STATEMENT,
                sourceName, hash(counterparty, date, "S"), date));

        ledger.forEach(entry -> entry.setBatchId(ledgerBatch.getId()));
        statement.forEach(entry -> entry.setBatchId(statementBatch.getId()));
        ledgerEntries.saveAll(ledger);
        statementEntries.saveAll(statement);

        ledgerBatch.markLoaded(ledger.size(), 0);
        statementBatch.markLoaded(statement.size(), 0);
        batches.save(ledgerBatch);
        batches.save(statementBatch);
    }

    /** Seeded rows are written straight to the tables, so they get the same kind of
     *  content hash a real load would produce rather than a placeholder. */
    private static String hash(Counterparty counterparty, LocalDate date, String side) {
        return dev.sellerkit.reconkit.ingest.CsvIngestor.sha256(
                "%s|%s|%s".formatted(counterparty.getCode(), date, side)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static long amountFor(Random random) {
        long[] common = {9_900L, 15_000L, 23_000L, 33_000L, 49_000L, 78_000L, 129_000L, 250_000L};
        return common[random.nextInt(common.length)] + random.nextInt(20) * 100L;
    }

    private void fill(TransactionEntry entry, Counterparty counterparty, String txnId, String approvalNo,
                      String orderId, Instant occurredAt, LocalDate businessDate, long gross, long fee,
                      TxnStatus status, String method, String cardBin) {
        entry.setCounterpartyId(counterparty.getId());
        entry.setExternalTxnId(txnId);
        entry.setApprovalNo(approvalNo);
        entry.setOrderId(orderId);
        entry.setMerchantNo(counterparty.getCode() + "-001");
        entry.setOccurredAt(occurredAt);
        entry.setBusinessDate(businessDate);
        entry.setGrossMinorUnits(gross);
        entry.setFeeMinorUnits(fee);
        entry.setNetMinorUnits(gross - fee);
        entry.setCurrency(counterparty.getCurrency());
        entry.setTxnStatus(status);
        entry.setPaymentMethod(method);
        entry.setCardBin(cardBin);
    }
}
