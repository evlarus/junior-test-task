package com.evlarus.spendinglimit.transaction.application;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    private final Duration firstDelay;
    private final Duration maxDelay;

    public RetryPolicy(TransactionsProperties properties) {
        this.firstDelay = properties.retry().firstDelay();
        this.maxDelay = properties.retry().maxDelay();
    }

    public Instant nextAttemptAfter(int failedAttempts, Instant now) {
        int doublings = Math.min(Math.max(failedAttempts - 1, 0), 30);
        Duration delay = firstDelay.multipliedBy(1L << doublings);
        return now.plus(delay.compareTo(maxDelay) > 0 ? maxDelay : delay);
    }
}
