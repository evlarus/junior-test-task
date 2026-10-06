package com.evlarus.spendinglimit.transaction.domain;

import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.common.domain.Timestamps;
import java.time.Instant;
import java.util.Objects;

/**
 * Outcome of processing a transaction. Final: the flag is never recalculated, even when a new limit is set later.
 *
 * @param limitId the limit the transaction was checked against (shown in the report of exceeded transactions)
 */
public record ProcessingResult(Money amountUsd, long limitId, boolean limitExceeded, Instant processedAt) {

    public ProcessingResult {
        Objects.requireNonNull(amountUsd, "amountUsd");
        Objects.requireNonNull(processedAt, "processedAt");
        if (!amountUsd.isIn(Money.USD) || amountUsd.isNegative()) {
            throw new IllegalArgumentException("Processed amount must be a non-negative USD amount, got " + amountUsd);
        }
        processedAt = Timestamps.normalize(processedAt);
    }
}
