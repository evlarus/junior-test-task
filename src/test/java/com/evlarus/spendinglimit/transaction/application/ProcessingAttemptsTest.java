package com.evlarus.spendinglimit.transaction.application;

import static com.evlarus.spendinglimit.support.DomainFixtures.CLIENT;
import static com.evlarus.spendinglimit.support.DomainFixtures.COUNTERPARTY;
import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.DomainFixtures.UTC_CALENDAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.rate.application.ExchangeRateService;
import com.evlarus.spendinglimit.rate.application.RateLookup;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.rate.domain.RateKind;
import com.evlarus.spendinglimit.transaction.domain.ProcessingResult;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

class ProcessingAttemptsTest {

    private static final Instant NOW = Instant.parse("2022-01-03T12:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2022, 1, 3);
    private static final ExchangeRate RATE = new ExchangeRate(KZT, DAY, new BigDecimal("450"), DAY, RateKind.CLOSE);
    private static final Transaction PENDING = new Transaction(
            7L, CLIENT, COUNTERPARTY, Money.of("4500", KZT), ExpenseCategory.PRODUCT,
            OffsetDateTime.parse("2022-01-03T10:00:00Z"), NOW, null);

    private final ExchangeRateService rates = mock(ExchangeRateService.class);
    private final TransactionProcessor processor = mock(TransactionProcessor.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final PendingTransactionQueue queue = mock(PendingTransactionQueue.class);
    private final ProcessingAttempts attempts = new ProcessingAttempts(
            rates, processor, transactions, queue,
            new RetryPolicy(new TransactionsProperties(Duration.ofMinutes(5), new TransactionsProperties.Retry(
                    Duration.ofSeconds(30), Duration.ofHours(1), Duration.ofMinutes(2), 50, 8))),
            UTC_CALENDAR, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void processesWithTheRateOfTheTransactionDay() {
        Transaction processed = processed();
        when(rates.findRate(KZT, DAY)).thenReturn(new RateLookup.Found(RATE));
        when(processor.process(7L, RATE)).thenReturn(Optional.of(processed));

        assertThat(attempts.attempt(PENDING, 0)).isSameAs(processed);
        verify(queue, never()).scheduleRetry(anyLong(), any(), anyString());
    }

    @Test
    void unavailableRateLeavesTheTransactionPendingWithBackoff() {
        when(rates.findRate(KZT, DAY)).thenReturn(new RateLookup.Unavailable("Twelve Data responded with 503"));

        Transaction result = attempts.attempt(PENDING, 2);

        assertThat(result.isPending()).isTrue();
        verify(queue).scheduleRetry(7L, NOW.plusSeconds(120), "Twelve Data responded with 503");
        verify(processor, never()).process(anyLong(), any());
    }

    @Test
    void lockTimeoutLeavesTheTransactionPending() {
        when(rates.findRate(KZT, DAY)).thenReturn(new RateLookup.Found(RATE));
        when(processor.process(7L, RATE)).thenThrow(new CannotAcquireLockException("lock timeout"));

        assertThat(attempts.attempt(PENDING, 0).isPending()).isTrue();
        verify(queue).scheduleRetry(eq(7L), eq(NOW.plusSeconds(30)), anyString());
    }

    @Test
    void transactionProcessedConcurrentlyIsReturnedAsStored() {
        Transaction processedElsewhere = processed();
        when(rates.findRate(KZT, DAY)).thenReturn(new RateLookup.Found(RATE));
        when(processor.process(7L, RATE)).thenReturn(Optional.empty());
        when(transactions.findById(7L)).thenReturn(Optional.of(processedElsewhere));

        assertThat(attempts.attempt(PENDING, 0)).isSameAs(processedElsewhere);
    }

    private static Transaction processed() {
        return new Transaction(7L, CLIENT, COUNTERPARTY, Money.of("4500", KZT), ExpenseCategory.PRODUCT,
                PENDING.occurredAt(), NOW, new ProcessingResult(Money.usd("10"), 1, false, NOW));
    }
}
