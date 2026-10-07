package com.evlarus.spendinglimit.transaction.application;

import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.application.LimitsProperties;
import com.evlarus.spendinglimit.limit.domain.LimitCheck;
import com.evlarus.spendinglimit.limit.domain.MonthlySpending;
import com.evlarus.spendinglimit.limit.domain.MonthlySpendingRepository;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.limit.domain.SpendingLimitRepository;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessor.class);

    private final TransactionTemplate transactionTemplate;
    private final TransactionRepository transactions;
    private final MonthlySpendingRepository spendings;
    private final SpendingLimitRepository limits;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final Money defaultLimit;
    private final MeterRegistry meterRegistry;

    public TransactionProcessor(
            TransactionTemplate transactionTemplate,
            TransactionRepository transactions,
            MonthlySpendingRepository spendings,
            SpendingLimitRepository limits,
            BusinessCalendar calendar,
            Clock clock,
            LimitsProperties limitsProperties,
            MeterRegistry meterRegistry) {
        this.transactionTemplate = transactionTemplate;
        this.transactions = transactions;
        this.spendings = spendings;
        this.limits = limits;
        this.calendar = calendar;
        this.clock = clock;
        this.defaultLimit = new Money(limitsProperties.defaultAmount(), Money.USD);
        this.meterRegistry = meterRegistry;
    }

    public Optional<Transaction> process(long transactionId, ExchangeRate rate) {
        Optional<Transaction> processed = transactionTemplate.execute(status -> processLocked(transactionId, rate));
        return processed == null ? Optional.empty() : processed;
    }

    private Optional<Transaction> processLocked(long transactionId, ExchangeRate rate) {
        Transaction transaction = transactions.lockById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction %d does not exist".formatted(transactionId)));
        if (!transaction.isPending()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        MonthlySpending spending = spendings.lockOrCreate(
                transaction.accountFrom(), transaction.category(), transaction.businessMonth(calendar));
        SpendingLimit limit = limitInForce(transaction);

        LimitCheck check = transaction.process(rate, spending, limit, calendar, now);

        spendings.save(spending);
        transactions.saveProcessingResult(transaction);
        meterRegistry.counter("transactions.processed", "limit_exceeded", String.valueOf(check.exceeded())).increment();
        log.info("Transaction {} of account {} processed: limit {} {}, remaining {}",
                transactionId, transaction.accountFrom(), limit.id(),
                check.exceeded() ? "exceeded" : "not exceeded", check.remaining());
        return Optional.of(transaction);
    }

    private SpendingLimit limitInForce(Transaction transaction) {
        Instant occurredAt = transaction.occurredAt().toInstant();
        return limits.findLatestClientLimit(transaction.accountFrom(), transaction.category(), occurredAt)
                .orElseGet(() -> limits.findOrCreateSystemDefault(SpendingLimit.systemDefault(
                        transaction.accountFrom(), transaction.category(), defaultLimit, occurredAt)));
    }
}
