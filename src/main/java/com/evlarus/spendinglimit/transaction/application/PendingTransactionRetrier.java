package com.evlarus.spendinglimit.transaction.application;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.transaction.application.PendingTransactionQueue.DueTransaction;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PendingTransactionRetrier {

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionRetrier.class);

    private final PendingTransactionQueue queue;
    private final TransactionRepository transactions;
    private final ProcessingAttempts attempts;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final TransactionsProperties.Retry retry;

    public PendingTransactionRetrier(
            PendingTransactionQueue queue,
            TransactionRepository transactions,
            ProcessingAttempts attempts,
            BusinessCalendar calendar,
            Clock clock,
            TransactionsProperties properties) {
        this.queue = queue;
        this.transactions = transactions;
        this.attempts = attempts;
        this.calendar = calendar;
        this.clock = clock;
        this.retry = properties.retry();
    }

    public int retryDue() {
        Instant now = clock.instant();
        List<DueTransaction> due = queue.claimDue(
                now, now.minus(retry.firstDelay()), now.plus(retry.lease()), retry.batchSize());
        if (due.isEmpty()) {
            return 0;
        }
        Map<Long, Integer> failedAttempts = due.stream()
                .collect(Collectors.toMap(DueTransaction::id, DueTransaction::failedAttempts));
        List<Transaction> claimed = transactions.findAllById(failedAttempts.keySet());

        Semaphore permits = new Semaphore(retry.parallelism());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (List<Transaction> group : groupBySpending(claimed)) {
                executor.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        group.forEach(transaction -> retryOne(transaction, failedAttempts.get(transaction.id())));
                    } finally {
                        permits.release();
                    }
                });
            }
        }
        log.info("Retried {} pending transactions", claimed.size());
        return claimed.size();
    }

    private Collection<List<Transaction>> groupBySpending(List<Transaction> claimed) {
        return claimed.stream()
                .sorted(Comparator.comparing((Transaction transaction) -> transaction.occurredAt().toInstant())
                        .thenComparing(Transaction::id))
                .collect(Collectors.groupingBy(
                        transaction -> new SpendingKey(transaction.accountFrom(), transaction.category(),
                                transaction.businessMonth(calendar)),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values();
    }

    private void retryOne(Transaction transaction, int failedAttempts) {
        try {
            attempts.attempt(transaction, failedAttempts);
        } catch (RuntimeException e) {
            log.error("Retry of transaction {} failed, it will be claimed again after the lease", transaction.id(), e);
        }
    }

    private record SpendingKey(AccountNumber account, ExpenseCategory category, YearMonth month) {
    }
}
