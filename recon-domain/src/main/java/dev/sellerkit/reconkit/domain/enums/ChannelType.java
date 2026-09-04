package dev.sellerkit.reconkit.domain.enums;

public enum ChannelType {
    /** Card payment gateway. Files arrive daily, settlement is T plus n. */
    PAYMENT_GATEWAY,
    /** Open market platform. Settles in batches, one statement line per payout. */
    OPEN_MARKET,
    /** App store in app purchase. Monthly, in a foreign currency, with its own fee model. */
    APP_STORE,
    /** Bank transfer or virtual account. */
    BANK
}
