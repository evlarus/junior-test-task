package com.evlarus.spendinglimit.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {

    private final AtomicReference<Instant> fixedInstant = new AtomicReference<>();

    public void setTo(Instant instant) {
        fixedInstant.set(instant);
    }

    public void setTo(String isoInstant) {
        setTo(Instant.parse(isoInstant));
    }

    public void reset() {
        fixedInstant.set(null);
    }

    @Override
    public Instant instant() {
        Instant fixed = fixedInstant.get();
        return fixed != null ? fixed : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("The application works with instants in UTC");
    }
}
