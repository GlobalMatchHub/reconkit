package dev.sellerkit.reconkit.ingest;

import dev.sellerkit.reconkit.domain.enums.TxnStatus;
import java.util.Map;

/**
 * How one counterparty's file is shaped.
 *
 * <p>No two settlement files agree on anything: column names, date formats, whether a
 * refund is a negative amount or a separate status, whether the amount includes the fee.
 * The choice here is to keep every one of those differences in data rather than in code,
 * because the alternative is a parser per counterparty and a release every time a new one
 * is signed.
 *
 * @param statusMap the counterparty's own status vocabulary, mapped onto ours. A value
 *                  not in this map is rejected rather than defaulted: guessing that an
 *                  unknown status means PAID is how a cancellation gets settled.
 */
public record MappingProfile(
        String name,
        char delimiter,
        String charset,
        boolean hasHeader,
        String timestampFormat,
        String timestampColumn,
        String externalTxnIdColumn,
        String approvalNoColumn,
        String orderIdColumn,
        String merchantNoColumn,
        String grossColumn,
        String feeColumn,
        String netColumn,
        String currencyColumn,
        String defaultCurrency,
        String statusColumn,
        String paymentMethodColumn,
        String cardBinColumn,
        Map<String, TxnStatus> statusMap) {

    public MappingProfile {
        statusMap = Map.copyOf(statusMap);
    }
}
