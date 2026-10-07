package com.evlarus.spendinglimit.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2022-01-03T12:00:00Z");

    private final RetryPolicy policy = new RetryPolicy(new TransactionsProperties(
            Duration.ofMinutes(5),
            new TransactionsProperties.Retry(Duration.ofSeconds(30), Duration.ofHours(1), Duration.ofMinutes(2), 50, 8)));

    @ParameterizedTest(name = "after {0} failed attempts wait {1} s")
    @CsvSource({
        "0, 30",
        "1, 30",
        "2, 60",
        "3, 120",
        "7, 1920",
        "8, 3600",
        "100, 3600"
    })
    void doublesTheDelayUpToTheMaximum(int failedAttempts, long expectedSeconds) {
        assertThat(policy.nextAttemptAfter(failedAttempts, NOW)).isEqualTo(NOW.plusSeconds(expectedSeconds));
    }
}
