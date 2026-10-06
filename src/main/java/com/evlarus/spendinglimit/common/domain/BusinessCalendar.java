package com.evlarus.spendinglimit.common.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The single time zone in which the service counts calendar days and months.
 *
 * <p>Transactions arrive with different offsets; a month boundary or "the day of a transaction"
 * only makes sense in one agreed zone, otherwise the same moment could belong to two different months.
 */
public record BusinessCalendar(ZoneId zone) {

    public BusinessCalendar {
        Objects.requireNonNull(zone, "zone");
    }

    public LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }

    public YearMonth monthOf(Instant instant) {
        return YearMonth.from(instant.atZone(zone));
    }
}
