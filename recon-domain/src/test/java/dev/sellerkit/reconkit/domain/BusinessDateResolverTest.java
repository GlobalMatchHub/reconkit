package dev.sellerkit.reconkit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sellerkit.reconkit.domain.policy.BusinessDateResolver;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessDateResolverTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static Instant at(int day, int hour, int minute) {
        return LocalDate.of(2026, 8, 17).plusDays(day).atTime(hour, minute).atZone(SEOUL).toInstant();
    }

    @Test
    @DisplayName("with a midnight cutoff the business date is simply the local date")
    void midnight() {
        BusinessDateResolver resolver = BusinessDateResolver.midnight(SEOUL);
        assertThat(resolver.resolve(at(0, 23, 59))).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(resolver.resolve(at(1, 0, 1))).isEqualTo(LocalDate.of(2026, 8, 18));
    }

    @Test
    @DisplayName("a 23:30 cutoff pushes the last half hour of the day onto the next one")
    void cutoffMovesTheDay() {
        BusinessDateResolver resolver = new BusinessDateResolver(SEOUL, LocalTime.of(23, 30));
        assertThat(resolver.resolve(at(0, 23, 29))).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(resolver.resolve(at(0, 23, 30))).isEqualTo(LocalDate.of(2026, 8, 18));
    }

    @Test
    @DisplayName("the distance to the cutoff is what separates a timing artefact from a lost transaction")
    void minutesToCutoff() {
        BusinessDateResolver resolver = new BusinessDateResolver(SEOUL, LocalTime.of(23, 30));
        assertThat(resolver.minutesToCutoff(at(0, 23, 25))).isEqualTo(5L);
        assertThat(resolver.minutesToCutoff(at(0, 14, 30))).isEqualTo(540L);
    }

    @Test
    @DisplayName("the same instant lands on different dates in different zones")
    void zoneMatters() {
        Instant instant = at(0, 8, 0);
        assertThat(BusinessDateResolver.midnight(SEOUL).resolve(instant))
                .isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(BusinessDateResolver.midnight(ZoneId.of("UTC")).resolve(instant))
                .isEqualTo(LocalDate.of(2026, 8, 16));
    }
}
