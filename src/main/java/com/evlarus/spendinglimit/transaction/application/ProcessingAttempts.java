package com.evlarus.spendinglimit.transaction.application;

import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import com.evlarus.spendinglimit.rate.application.ExchangeRateService;
import com.evlarus.spendinglimit.rate.application.RateLookup;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

@Component
public class ProcessingAttempts {

    private static final Logger log = LoggerFactory.getLogger(ProcessingAttempts.class);

    private final ExchangeRateService rates;
    private final TransactionProcessor processor;
    private final TransactionRepository transactions;
    private final PendingTransactionQueue queue;
    private final RetryPolicy retryPolicy;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public ProcessingAttempts(
            ExchangeRateService rates,
            TransactionProcessor processor,
            TransactionRepository transactions,
            PendingTransactionQueue queue,
            RetryPolicy retryPolicy,
            BusinessCalendar calendar,
            Clock clock) {
        this.rates = rates;
        this.processor = processor;
        this.transactions = transactions;
        this.queue = queue;
        this.retryPolicy = retryPolicy;
        this.calendar = calendar;
        this.clock = clock;
    }

    public Transaction attempt(Transaction transaction, int failedAttempts) {
        RateLookup lookup = rates.findRate(transaction.amount().currency(), transaction.businessDate(calendar));
        return switch (lookup) {
            case RateLookup.Found(ExchangeRate rate) -> processWith(transaction, rate, failedAttempts);
            case RateLookup.Unavailable(String reason) -> postpone(transaction, failedAttempts, reason);
        };
    }

    private Transaction processWith(Transaction transaction, ExchangeRate rate, int failedAttempts) {
        long id = Objects.requireNonNull(transaction.id(), "transaction id");
        try {
            return processor.process(id, rate)
                    .orElseGet(() -> transactions.findById(id).orElse(transaction));
        } catch (TransientDataAccessException e) {
            return postpone(transaction, failedAttempts, "Database is busy: " + e.getMostSpecificCause().getMessage());
        }
    }

    private Transaction postpone(Transaction transaction, int failedAttempts, String reason) {
        long id = Objects.requireNonNull(transaction.id(), "transaction id");
        Instant nextAttempt = retryPolicy.nextAttemptAfter(failedAttempts + 1, clock.instant());
        queue.scheduleRetry(id, nextAttempt, reason);
        log.warn("Transaction {} stays pending until {}: {}", id, nextAttempt, reason);
        return transaction;
    }
}
