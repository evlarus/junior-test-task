package com.evlarus.spendinglimit.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

    // 31 January 20:00 UTC is already 1 February 02:00 in Almaty (UTC+6 in 2022)
    private static final Instant LATE_EVENING_UTC = Instant.parse("2022-01-31T20:00:00Z");

    @Test
    void monthDependsOnTheBusinessZone() {
        assertThat(new BusinessCalendar(ZoneOffset.UTC).monthOf(LATE_EVENING_UTC)).isEqualTo(YearMonth.of(2022, 1));
        assertThat(new BusinessCalendar(ZoneId.of("Asia/Almaty")).monthOf(LATE_EVENING_UTC))
                .isEqualTo(YearMonth.of(2022, 2));
    }

    @Test
    void dateDependsOnTheBusinessZone() {
        assertThat(new BusinessCalendar(ZoneOffset.UTC).dateOf(LATE_EVENING_UTC)).isEqualTo(LocalDate.of(2022, 1, 31));
        assertThat(new BusinessCalendar(ZoneId.of("Asia/Almaty")).dateOf(LATE_EVENING_UTC))
                .isEqualTo(LocalDate.of(2022, 2, 1));
    }

    @Test
    void firstAndLastMicrosecondOfMonthBelongToIt() {
        BusinessCalendar calendar = new BusinessCalendar(ZoneOffset.UTC);

        assertThat(calendar.monthOf(Instant.parse("2022-02-01T00:00:00Z"))).isEqualTo(YearMonth.of(2022, 2));
        assertThat(calendar.monthOf(Instant.parse("2022-01-31T23:59:59.999999Z"))).isEqualTo(YearMonth.of(2022, 1));
    }
}
