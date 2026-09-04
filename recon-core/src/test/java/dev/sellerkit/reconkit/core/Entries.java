package dev.sellerkit.reconkit.core;

import dev.sellerkit.reconkit.domain.enums.TxnStatus;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import dev.sellerkit.reconkit.domain.model.TransactionEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Fixture builder. Every field a matching pass looks at is settable from one place. */
final class Entries {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final LocalDate DAY = LocalDate.of(2026, 8, 17);

    private Entries() {
    }

    static SettlementTerms terms() {
        SettlementTerms terms = new SettlementTerms();
        terms.setFeeRateBasisPoints(220);
        terms.setFixedFeeMinorUnits(0L);
        terms.setFeeVatBasisPoints(1000);
        terms.setAmountToleranceAbsolute(0L);
        terms.setAmountToleranceBasisPoints(0);
        terms.setFeeToleranceAbsolute(1L);
        terms.setFeeToleranceBasisPoints(10);
        terms.setMatchWindowDays(1);
        terms.setCutoffTime(LocalTime.of(23, 30));
        return terms;
    }

    static LedgerEntry ledger(long id, String txnId, long gross, int hour, int minute) {
        LedgerEntry entry = new LedgerEntry();
        fill(entry, id, txnId, gross, hour, minute);
        return entry;
    }

    static StatementEntry statement(long id, String txnId, long gross, int hour, int minute) {
        StatementEntry entry = new StatementEntry();
        fill(entry, id, txnId, gross, hour, minute);
        // A statement line normally carries the fee the counterparty actually withheld.
        entry.setFeeMinorUnits(expectedFee(gross));
        return entry;
    }

    /** 2.20 percent plus 10 percent tax on the commission, rounded half up. */
    static long expectedFee(long gross) {
        long commission = (Math.abs(gross) * 220 + 5_000) / 10_000;
        long tax = (commission * 1000 + 5_000) / 10_000;
        return commission + tax;
    }

    private static void fill(TransactionEntry entry, long id, String txnId, long gross, int hour, int minute) {
        entry.setId(id);
        entry.setBatchId(1L);
        entry.setCounterpartyId(1L);
        entry.setExternalTxnId(txnId);
        entry.setApprovalNo("A" + txnId);
        entry.setOrderId("O" + txnId);
        entry.setMerchantNo("M001");
        entry.setOccurredAt(at(hour, minute));
        entry.setBusinessDate(DAY);
        entry.setGrossMinorUnits(gross);
        entry.setNetMinorUnits(gross);
        entry.setCurrency("KRW");
        entry.setTxnStatus(TxnStatus.PAID);
        entry.setPaymentMethod("CARD");
        entry.setCardBin("450000");
    }

    static Instant at(int hour, int minute) {
        return DAY.atTime(hour, minute).atZone(SEOUL).toInstant();
    }
}
