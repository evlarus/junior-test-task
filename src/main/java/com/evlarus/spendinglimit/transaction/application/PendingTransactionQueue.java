package com.evlarus.spendinglimit.transaction.application;

import java.time.Instant;
import java.util.List;

public interface PendingTransactionQueue {

    List<DueTransaction> claimDue(Instant now, Instant freshBefore, Instant leaseUntil, int limit);

    void scheduleRetry(long transactionId, Instant nextAttemptAt, String reason);

    long countPending();

    record DueTransaction(long id, int failedAttempts) {
    }
}
