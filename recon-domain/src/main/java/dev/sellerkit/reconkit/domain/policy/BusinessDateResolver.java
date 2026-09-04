package dev.sellerkit.reconkit.domain.policy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Turns an instant into the settlement day it belongs to.
 *
 * <p>Our own systems roll the day at local midnight. A counterparty rolls it at its own
 * cutoff, and 23:30 is a common one. Everything that happens in that half hour is on
 * our books for today and on theirs for tomorrow, and if the date is taken from the
 * file rather than derived, the engine reports the same block of transactions missing
 * every single night, always at the same hour, always for amounts that add up.
 *
 * <p>So the date is computed here, once, from the instant and the counterparty's cutoff.
 * The value in the file is not trusted, because the file was written by whoever we are
 * checking.
 */
public final class BusinessDateResolver {

    private final ZoneId zone;
    private final LocalTime cutoff;

    public BusinessDateResolver(ZoneId zone, LocalTime cutoff) {
        this.zone = zone;
        this.cutoff = cutoff;
    }

    /** Midnight cutoff: the business date is simply the local date. */
    public static BusinessDateResolver midnight(ZoneId zone) {
        return new BusinessDateResolver(zone, LocalTime.MIDNIGHT);
    }

    public LocalDate resolve(Instant occurredAt) {
        ZonedDateTime local = occurredAt.atZone(zone);
        if (cutoff.equals(LocalTime.MIDNIGHT)) {
            return local.toLocalDate();
        }
        return local.toLocalTime().isBefore(cutoff)
                ? local.toLocalDate()
                : local.toLocalDate().plusDays(1);
    }

    /**
     * Minutes between the transaction and the next cutoff. A small number here is the
     * difference between "the counterparty lost our transaction" and "it will be on
     * tomorrow's file", and the two deserve very different severities.
     */
    public long minutesToCutoff(Instant occurredAt) {
        if (cutoff.equals(LocalTime.MIDNIGHT)) {
            ZonedDateTime local = occurredAt.atZone(zone);
            ZonedDateTime nextMidnight = local.toLocalDate().plusDays(1).atStartOfDay(zone);
            return java.time.Duration.between(local, nextMidnight).toMinutes();
        }
        ZonedDateTime local = occurredAt.atZone(zone);
        ZonedDateTime boundary = local.toLocalTime().isBefore(cutoff)
                ? local.toLocalDate().atTime(cutoff).atZone(zone)
                : local.toLocalDate().plusDays(1).atTime(cutoff).atZone(zone);
        return java.time.Duration.between(local, boundary).toMinutes();
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalTime cutoff() {
        return cutoff;
    }
}
