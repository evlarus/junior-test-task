package com.evlarus.spendinglimit.rate.application;

import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import java.util.Objects;

/** Outcome of looking up a rate: either a rate or the reason there is none right now. */
public sealed interface RateLookup {

    record Found(ExchangeRate rate) implements RateLookup {

        public Found {
            Objects.requireNonNull(rate, "rate");
        }
    }

    /** No rate at the moment; the caller keeps its work pending and tries again later. */
    record Unavailable(String reason) implements RateLookup {

        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
