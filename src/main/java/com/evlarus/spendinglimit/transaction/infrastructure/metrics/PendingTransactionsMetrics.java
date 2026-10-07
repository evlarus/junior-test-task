package com.evlarus.spendinglimit.transaction.infrastructure.metrics;

import com.evlarus.spendinglimit.transaction.application.PendingTransactionQueue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
class PendingTransactionsMetrics implements MeterBinder {

    private final PendingTransactionQueue queue;

    PendingTransactionsMetrics(PendingTransactionQueue queue) {
        this.queue = queue;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("transactions.pending", queue, PendingTransactionQueue::countPending)
                .description("Transactions waiting for an exchange rate")
                .register(registry);
    }
}
