package com.evlarus.spendinglimit.common.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Time precision of the system is one microsecond (that is what PostgreSQL {@code timestamptz} stores).
 *
 * <p>Java clocks produce nanoseconds; without truncation a value read back from the database would differ
 * from the one that was saved. Domain objects truncate every timestamp once, when they are created.
 */
public final class Timestamps {

    private Timestamps() {
    }

    public static Instant normalize(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    public static OffsetDateTime normalize(OffsetDateTime dateTime) {
        return dateTime.truncatedTo(ChronoUnit.MICROS);
    }
}
