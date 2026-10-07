package com.evlarus.spendinglimit.transaction.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.transactions")
@Validated
public record TransactionsProperties(@NotNull Duration allowedClockSkew, @NotNull @Valid Retry retry) {

    public record Retry(
            @NotNull Duration firstDelay,
            @NotNull Duration maxDelay,
            @NotNull Duration lease,
            @Min(1) @Max(1000) int batchSize,
            @Min(1) @Max(64) int parallelism) {
    }
}
