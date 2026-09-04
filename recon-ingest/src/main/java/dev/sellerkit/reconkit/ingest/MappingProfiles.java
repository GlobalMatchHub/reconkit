package dev.sellerkit.reconkit.ingest;

import dev.sellerkit.reconkit.domain.enums.TxnStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The profiles shipped with the system. Real deployments add rows to a table; these three
 * exist because they are the three shapes that actually turn up.
 */
public final class MappingProfiles {

    /** Our own export. The one file whose shape we control. */
    public static final MappingProfile INTERNAL_LEDGER = new MappingProfile(
            "internal-ledger", ',', "UTF-8", true,
            "yyyy-MM-dd HH:mm:ss", "approved_at",
            "pg_txn_id", "approval_no", "order_id", "merchant_no",
            "amount", "fee", "net_amount", null, "KRW",
            "status", "method", "card_bin",
            Map.of("PAID", TxnStatus.PAID,
                    "REFUND", TxnStatus.REFUND,
                    "PARTIAL_REFUND", TxnStatus.PARTIAL_REFUND,
                    "CANCEL", TxnStatus.CANCEL,
                    "CHARGEBACK", TxnStatus.CHARGEBACK));

    /**
     * A card gateway daily file. Per transaction, fee already withheld, and a status
     * column that spells cancellation two different ways depending on when it happened.
     */
    public static final MappingProfile GATEWAY_DAILY = new MappingProfile(
            "gateway-daily", ',', "UTF-8", true,
            "yyyyMMddHHmmss", "TRAN_DTTM",
            "TID", "APPR_NO", "ORD_NO", "MID",
            "AMT", "FEE_AMT", "NET_AMT", null, "KRW",
            "TRAN_TYPE", "PAY_MEAN", "CARD_BIN",
            statusVocabulary());

    /**
     * A marketplace payout file. One line per batch, no transaction identifier at all,
     * which is what pass D exists for.
     */
    public static final MappingProfile MARKETPLACE_PAYOUT = new MappingProfile(
            "marketplace-payout", ',', "UTF-8", true,
            "yyyy-MM-dd HH:mm", "정산일시",
            null, null, "정산번호", "판매자번호",
            "정산금액", "수수료", "지급액", null, "KRW",
            "구분", null, null,
            Map.of("판매", TxnStatus.PAID,
                    "환불", TxnStatus.REFUND,
                    "취소", TxnStatus.CANCEL));

    private static final Map<String, MappingProfile> REGISTRY = new LinkedHashMap<>();

    static {
        register(INTERNAL_LEDGER);
        register(GATEWAY_DAILY);
        register(MARKETPLACE_PAYOUT);
    }

    private MappingProfiles() {
    }

    private static Map<String, TxnStatus> statusVocabulary() {
        Map<String, TxnStatus> map = new LinkedHashMap<>();
        map.put("승인", TxnStatus.PAID);
        map.put("매입", TxnStatus.PAID);
        map.put("승인취소", TxnStatus.CANCEL);
        map.put("매입취소", TxnStatus.REFUND);
        map.put("부분취소", TxnStatus.PARTIAL_REFUND);
        map.put("이의제기", TxnStatus.CHARGEBACK);
        return map;
    }

    public static void register(MappingProfile profile) {
        REGISTRY.put(profile.name(), profile);
    }

    public static Optional<MappingProfile> find(String name) {
        return Optional.ofNullable(REGISTRY.get(name));
    }

    public static MappingProfile require(String name) {
        return find(name).orElseThrow(() ->
                new IllegalArgumentException("no mapping profile named " + name));
    }

    public static Map<String, MappingProfile> all() {
        return Map.copyOf(REGISTRY);
    }
}
