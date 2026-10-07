package com.evlarus.spendinglimit.transaction.infrastructure.persistence;

import static com.evlarus.spendinglimit.support.DomainFixtures.COUNTERPARTY;
import static org.assertj.core.api.Assertions.assertThat;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.evlarus.spendinglimit.support.TestAccounts;
import com.evlarus.spendinglimit.transaction.application.PendingTransactionQueue;
import com.evlarus.spendinglimit.transaction.application.PendingTransactionQueue.DueTransaction;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class PendingTransactionQueueIT {

    private static final Instant RECEIVED_AT = Instant.parse("2020-06-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2020-06-01T12:00:00Z");

    @Autowired
    private PendingTransactionQueue queue;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private JdbcClient jdbc;

    private final AccountNumber account = TestAccounts.unique();

    @Test
    void freshTransactionIsClaimedOnlyAfterTheFirstDelayAndThenLeased() {
        long id = pending();

        assertThat(claimedIds(NOW, RECEIVED_AT.minusSeconds(1))).doesNotContain(id);
        assertThat(claimedIds(NOW, RECEIVED_AT)).contains(id);
        assertThat(claimedIds(NOW.plusSeconds(60), NOW.plusSeconds(60))).as("leased for two minutes").doesNotContain(id);
        assertThat(claimedIds(NOW.plusSeconds(120), NOW.plusSeconds(120))).contains(id);
    }

    @Test
    void scheduledRetryCountsTheAttemptAndKeepsTheReason() {
        long id = pending();

        queue.scheduleRetry(id, NOW.plusSeconds(30), "x".repeat(600));

        assertThat(jdbc.sql("select attempts, length(last_error) as error_length from bank_transaction where id = ?")
                .param(id)
                .query((row, n) -> List.of(row.getInt("attempts"), row.getInt("error_length")))
                .single()).containsExactly(1, 500);
        assertThat(claimedIds(NOW.plusSeconds(29), NOW.plusSeconds(29))).doesNotContain(id);
        assertThat(claimed(NOW.plusSeconds(30), NOW.plusSeconds(30)))
                .filteredOn(due -> due.id() == id)
                .singleElement()
                .extracting(DueTransaction::failedAttempts)
                .isEqualTo(1);
    }

    @Test
    void concurrentClaimsNeverReturnTheSameTransaction() throws Exception {
        IntStream.range(0, 20).forEach(i -> pending());
        Instant later = NOW.plus(Duration.ofDays(400));
        List<Callable<List<DueTransaction>>> claimers = IntStream.range(0, 4)
                .<Callable<List<DueTransaction>>>mapToObj(i -> () -> queue.claimDue(later, later, later.plusSeconds(120), 10))
                .toList();

        List<DueTransaction> all;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            all = executor.invokeAll(claimers).stream().map(PendingTransactionQueueIT::get).flatMap(List::stream).toList();
        }

        Set<Long> unique = new HashSet<>();
        assertThat(all).allMatch(due -> unique.add(due.id()));
    }

    private long pending() {
        return transactions.add(Transaction.receive(account, COUNTERPARTY, Money.usd("10"), ExpenseCategory.PRODUCT,
                OffsetDateTime.parse("2020-06-01T09:00:00Z"), RECEIVED_AT, Duration.ZERO)).id();
    }

    private List<Long> claimedIds(Instant now, Instant freshBefore) {
        return claimed(now, freshBefore).stream().map(DueTransaction::id).toList();
    }

    private List<DueTransaction> claimed(Instant now, Instant freshBefore) {
        return queue.claimDue(now, freshBefore, now.plusSeconds(120), 1000);
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
