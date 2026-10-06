package com.evlarus.spendinglimit.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TimestampsTest {

    @Test
    void truncatesInstantToMicroseconds() {
        assertThat(Timestamps.normalize(Instant.parse("2022-01-01T00:00:00.123456789Z")))
                .isEqualTo(Instant.parse("2022-01-01T00:00:00.123456Z"));
    }

    @Test
    void truncatesDateTimeToMicrosecondsAndKeepsOffset() {
        OffsetDateTime normalized = Timestamps.normalize(OffsetDateTime.parse("2022-01-01T00:00:00.123456789+06:00"));

        assertThat(normalized).isEqualTo(OffsetDateTime.parse("2022-01-01T00:00:00.123456+06:00"));
        assertThat(normalized.getOffset().getTotalSeconds()).isEqualTo(6 * 3600);
    }
}
