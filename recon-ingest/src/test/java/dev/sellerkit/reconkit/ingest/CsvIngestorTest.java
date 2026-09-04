package dev.sellerkit.reconkit.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sellerkit.reconkit.domain.enums.EntrySide;
import dev.sellerkit.reconkit.domain.enums.TxnStatus;
import dev.sellerkit.reconkit.domain.model.TransactionEntry;
import dev.sellerkit.reconkit.domain.policy.BusinessDateResolver;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsvIngestorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final CsvIngestor ingestor = new CsvIngestor();

    private static byte[] gatewayFile(String... rows) {
        StringBuilder text = new StringBuilder("TID,APPR_NO,ORD_NO,MID,TRAN_DTTM,AMT,FEE_AMT,NET_AMT,TRAN_TYPE,PAY_MEAN,CARD_BIN\n");
        for (String row : rows) {
            text.append(row).append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private IngestResult ingest(byte[] content, LocalTime cutoff) {
        return ingestor.ingest(content, MappingProfiles.GATEWAY_DAILY, EntrySide.STATEMENT, 1L,
                new BusinessDateResolver(SEOUL, cutoff));
    }

    @Test
    @DisplayName("a well formed file loads and the counterparty's status vocabulary is translated")
    void loadsRows() {
        IngestResult result = ingest(gatewayFile(
                "T1,A1,O1,M1,20260817143000,\"33,000\",871,32129,승인,CARD,450001",
                "T2,A2,O2,M1,20260817150000,10000,264,9736,매입취소,CARD,450002"), LocalTime.MIDNIGHT);

        assertThat(result.rejected()).isEmpty();
        assertThat(result.entries()).hasSize(2);
        TransactionEntry first = result.entries().get(0);
        assertThat(first.getExternalTxnId()).isEqualTo("T1");
        assertThat(first.getGrossMinorUnits()).isEqualTo(33_000L);
        assertThat(first.getTxnStatus()).isEqualTo(TxnStatus.PAID);
        assertThat(result.entries().get(1).getTxnStatus()).isEqualTo(TxnStatus.REFUND);
    }

    @Test
    @DisplayName("a refund's fee is stored negative, because the counterparty gives it back")
    void refundFeeIsSigned() {
        IngestResult result = ingest(gatewayFile(
                "T1,A1,O1,M1,20260817143000,50000,1355,48645,매입취소,CARD,450001"), LocalTime.MIDNIGHT);

        TransactionEntry entry = result.entries().get(0);
        assertThat(entry.getTxnStatus()).isEqualTo(TxnStatus.REFUND);
        assertThat(entry.signedGross().minorUnits()).isEqualTo(-50_000L);
        assertThat(entry.getFeeMinorUnits()).isEqualTo(-1_355L);
        assertThat(entry.getNetMinorUnits()).isEqualTo(-48_645L);
    }

    @Test
    @DisplayName("the business date is derived from the cutoff, not taken from the file")
    void businessDateComesFromTheCutoff() {
        IngestResult result = ingest(gatewayFile(
                "T1,A1,O1,M1,20260817233500,10000,264,9736,승인,CARD,450001"), LocalTime.of(23, 30));

        assertThat(result.entries().get(0).getBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 18));
    }

    @Test
    @DisplayName("a status the profile does not know is rejected rather than defaulted")
    void unknownStatusIsRejected() {
        IngestResult result = ingest(gatewayFile(
                "T1,A1,O1,M1,20260817143000,10000,264,9736,알수없음,CARD,450001"), LocalTime.MIDNIGHT);

        assertThat(result.entries()).isEmpty();
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).reason()).contains("unknown status");
    }

    @Test
    @DisplayName("one bad row does not stop the file")
    void partialFailure() {
        IngestResult result = ingest(gatewayFile(
                "T1,A1,O1,M1,20260817143000,10000,264,9736,승인,CARD,450001",
                "T2,A2,O2,M1,not-a-date,10000,264,9736,승인,CARD,450002",
                "T3,A3,O3,M1,20260817153000,20000,528,19472,승인,CARD,450003"), LocalTime.MIDNIGHT);

        assertThat(result.loadedCount()).isEqualTo(2);
        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.rejected().get(0).lineNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("the same bytes always hash the same, which is what stops a double load")
    void contentHashIsStable() {
        byte[] content = gatewayFile("T1,A1,O1,M1,20260817143000,10000,264,9736,승인,CARD,450001");
        assertThat(ingest(content, LocalTime.MIDNIGHT).contentHash())
                .isEqualTo(ingest(content, LocalTime.MIDNIGHT).contentHash())
                .hasSize(64);
    }

    @Test
    @DisplayName("amounts become minor units, and a value too precise for the currency is refused")
    void amountParsing() {
        assertThat(AmountParser.toMinorUnits("1,234", "KRW")).isEqualTo(1_234L);
        assertThat(AmountParser.toMinorUnits("(1,234)", "KRW")).isEqualTo(-1_234L);
        assertThat(AmountParser.toMinorUnits("12.34", "USD")).isEqualTo(1_234L);
        assertThat(AmountParser.toMinorUnits("12.30", "USD")).isEqualTo(1_230L);
        assertThatThrownBy(() -> AmountParser.toMinorUnits("12.345", "USD"))
                .hasMessageContaining("decimal places");
        assertThatThrownBy(() -> AmountParser.toMinorUnits("1.5", "KRW"))
                .hasMessageContaining("decimal places");
    }
}
