package com.evlarus.spendinglimit.limit.domain;

import com.evlarus.spendinglimit.common.domain.Money;
import java.util.Objects;

/**
 * Result of registering a transaction against a limit.
 *
 * @param remaining limit amount minus everything spent in the month, including this transaction;
 *                  negative when the limit is exceeded
 */
public record LimitCheck(Money remaining) {

    public LimitCheck {
        Objects.requireNonNull(remaining, "remaining");
    }

    /** Only a remainder below zero exceeds the limit: spending exactly the whole limit is still allowed. */
    public boolean exceeded() {
        return remaining.isNegative();
    }
}
