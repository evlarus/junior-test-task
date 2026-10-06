package com.evlarus.spendinglimit.limit.infrastructure.persistence;

import static com.evlarus.spendinglimit.common.domain.ExpenseCategory.PRODUCT;
import static com.evlarus.spendinglimit.common.domain.ExpenseCategory.SERVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.MonthlySpending;
import com.evlarus.spendinglimit.limit.domain.MonthlySpendingRepository;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.evlarus.spendinglimit.support.TestAccounts;
import java.time.Instant;
import java.time.YearMonth;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class MonthlySpendingRepositoryIT {

    private static final YearMonth JANUARY = YearMonth.of(2022, 1);

    @Autowired
    private MonthlySpendingRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final AccountNumber account = TestAccounts.unique();

    @Test
    void startsEmptyAndKeepsTheSavedTotal() {
        assertThat(inTransaction(() -> repository.lockOrCreate(account, PRODUCT, JANUARY).spent()))
                .isEqualTo(Money.usd("0"));

        spend(PRODUCT, JANUARY, "150.25");

        assertThat(inTransaction(() -> repository.lockOrCreate(account, PRODUCT, JANUARY).spent()))
                .isEqualTo(Money.usd("150.25"));
    }

    @Test
    void eachCategoryAndMonthHasItsOwnTotal() {
        spend(PRODUCT, JANUARY, "100");

        assertThat(inTransaction(() -> repository.lockOrCreate(account, SERVICE, JANUARY).spent()))
                .isEqualTo(Money.usd("0"));
        assertThat(inTransaction(() -> repository.lockOrCreate(account, PRODUCT, JANUARY.plusMonths(1)).spent()))
                .isEqualTo(Money.usd("0"));
    }

    @Test
    void lockingOutsideOfATransactionIsRefused() {
        assertThatThrownBy(() -> repository.lockOrCreate(account, PRODUCT, JANUARY))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void totalThatWasNotLockedCannotBeSaved() {
        spend(PRODUCT, JANUARY, "100");
        MonthlySpending notLocked = new MonthlySpending(account, PRODUCT, JANUARY, Money.usd("1"));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> repository.save(notLocked)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("must be locked");
        assertThat(inTransaction(() -> repository.lockOrCreate(account, PRODUCT, JANUARY).spent()))
                .isEqualTo(Money.usd("100"));
    }

    @Test
    void concurrentTransactionWaitsForTheLockAndThenSeesTheCommittedTotal() throws Exception {
        spend(PRODUCT, JANUARY, "0");
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                MonthlySpending spending = repository.lockOrCreate(account, PRODUCT, JANUARY);
                spending.register(Money.usd("100"), limit());
                repository.save(spending);
                firstHoldsLock.countDown();
                await(releaseFirst);
            }));
            assertThat(firstHoldsLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Money> second = executor.submit(
                    () -> inTransaction(() -> repository.lockOrCreate(account, PRODUCT, JANUARY).spent()));

            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                    .as("second transaction must wait while the first holds the lock")
                    .isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(Money.usd("100"));
        }
    }

    @Test
    void waitingForALockedTotalGivesUpAfterTheLockTimeout() throws Exception {
        spend(PRODUCT, JANUARY, "0");
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                repository.lockOrCreate(account, PRODUCT, JANUARY);
                firstHoldsLock.countDown();
                await(releaseFirst);
            }));
            assertThat(firstHoldsLock.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                // lock_timeout is 1s in the test profile
                assertThatThrownBy(() -> inTransaction(() -> repository.lockOrCreate(account, PRODUCT, JANUARY)))
                        .isInstanceOf(PessimisticLockingFailureException.class);
            } finally {
                releaseFirst.countDown();
                first.get(5, TimeUnit.SECONDS);
            }
        }
    }

    private void spend(ExpenseCategory category, YearMonth month, String usd) {
        transactionTemplate.executeWithoutResult(status -> {
            MonthlySpending spending = repository.lockOrCreate(account, category, month);
            spending.register(Money.usd(usd), new SpendingLimit(
                    1L, account, category, Money.usd("1000"), Instant.parse("2022-01-01T00:00:00Z"), false));
            repository.save(spending);
        });
    }

    private SpendingLimit limit() {
        return new SpendingLimit(1L, account, PRODUCT, Money.usd("1000"), Instant.parse("2022-01-01T00:00:00Z"), false);
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch was not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
