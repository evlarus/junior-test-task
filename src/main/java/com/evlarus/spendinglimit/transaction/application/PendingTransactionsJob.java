package com.evlarus.spendinglimit.transaction.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class PendingTransactionsJob {

    private final PendingTransactionRetrier retrier;

    PendingTransactionsJob(PendingTransactionRetrier retrier) {
        this.retrier = retrier;
    }

    @Scheduled(
            initialDelayString = "${app.transactions.retry.poll-interval}",
            fixedDelayString = "${app.transactions.retry.poll-interval}")
    void retryDue() {
        retrier.retryDue();
    }
}
