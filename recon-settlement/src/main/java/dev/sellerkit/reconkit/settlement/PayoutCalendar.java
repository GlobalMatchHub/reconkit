package dev.sellerkit.reconkit.settlement;

import dev.sellerkit.reconkit.domain.enums.SettlementCycle;
import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * When the money is due.
 *
 * <p>T plus two means two business days, not two days, and the difference is the reason
 * merchants call on Monday. Weekends are handled here; public holidays are deliberately
 * left as an injected set rather than hard coded, because a holiday table that ships
 * inside a jar is wrong the year after it ships.
 */
public final class PayoutCalendar {

    private final Set<LocalDate> holidays;

    public PayoutCalendar(Set<LocalDate> holidays) {
        this.holidays = Set.copyOf(holidays);
    }

    public static PayoutCalendar weekendsOnly() {
        return new PayoutCalendar(Set.of());
    }

    public boolean isBusinessDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !holidays.contains(date);
    }

    public LocalDate addBusinessDays(LocalDate from, int days) {
        LocalDate cursor = from;
        int remaining = days;
        while (remaining > 0) {
            cursor = cursor.plusDays(1);
            if (isBusinessDay(cursor)) {
                remaining--;
            }
        }
        while (!isBusinessDay(cursor)) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    public LocalDate payoutDateFor(LocalDate periodEnd, SettlementTerms terms) {
        return switch (terms.getCycle()) {
            case DAILY_T_PLUS_N -> addBusinessDays(periodEnd, terms.getSettleAfterDays());
            case WEEKLY, SEMI_MONTHLY, MONTHLY -> addBusinessDays(periodEnd, Math.max(1, terms.getSettleAfterDays()));
        };
    }

    /** Inclusive period bounds for the cycle that contains the given date. */
    public LocalDate[] periodFor(LocalDate date, SettlementCycle cycle) {
        return switch (cycle) {
            case DAILY_T_PLUS_N -> new LocalDate[]{date, date};
            case WEEKLY -> {
                LocalDate start = date.with(DayOfWeek.MONDAY);
                yield new LocalDate[]{start, start.plusDays(6)};
            }
            case SEMI_MONTHLY -> date.getDayOfMonth() <= 15
                    ? new LocalDate[]{date.withDayOfMonth(1), date.withDayOfMonth(15)}
                    : new LocalDate[]{date.withDayOfMonth(16),
                            date.withDayOfMonth(date.lengthOfMonth())};
            case MONTHLY -> new LocalDate[]{date.withDayOfMonth(1),
                    date.withDayOfMonth(date.lengthOfMonth())};
        };
    }
}
