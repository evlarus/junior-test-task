package com.evlarus.spendinglimit.limit.infrastructure.persistence;

import static com.evlarus.spendinglimit.common.domain.ExpenseCategory.PRODUCT;
import static com.evlarus.spendinglimit.common.domain.ExpenseCategory.SERVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.limit.domain.SpendingLimitRepository;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.evlarus.spendinglimit.support.TestAccounts;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class SpendingLimitRepositoryIT {

    @Autowired
    private SpendingLimitRepository repository;

    @Autowired
    private JdbcClient jdbc;

    private final AccountNumber account = TestAccounts.unique();

    @Test
    void addedLimitGetsAnIdAndIsReadBackUnchanged() {
        SpendingLimit saved = addClientLimit("1000.50", "2022-01-01T10:00:00.123456Z");

        assertThat(saved.id()).isNotNull();
        assertThat(repository.findLatestClientLimit(account, PRODUCT, saved.setAt())).contains(saved);
    }

    @Test
    void findsTheLatestClientLimitSetAtOrBeforeTheMoment() {
        SpendingLimit first = addClientLimit("1000", "2022-01-01T00:00:00Z");
        SpendingLimit second = addClientLimit("2000", "2022-01-10T00:00:00Z");
        createSystemDefault("2022-01-20T00:00:00Z");

        assertThat(findLatest("2021-12-31T23:59:59Z")).isEmpty();
        assertThat(findLatest("2022-01-09T23:59:59.999999Z")).contains(first);
        assertThat(findLatest("2022-01-10T00:00:00Z")).as("the moment itself is included").contains(second);
        assertThat(findLatest("2022-02-01T00:00:00Z")).as("a newer system default is ignored").contains(second);
        assertThat(repository.findLatestClientLimit(account, SERVICE, Instant.parse("2022-02-01T00:00:00Z"))).isEmpty();
    }

    @Test
    void savedLimitCannotBeAddedAgain() {
        SpendingLimit saved = addClientLimit("1000", "2022-01-01T00:00:00Z");

        assertThatThrownBy(() -> repository.add(saved))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void twoClientLimitsCannotBeSetAtTheSameMoment() {
        addClientLimit("1000", "2022-01-01T00:00:00Z");

        assertThatThrownBy(() -> addClientLimit("2000", "2022-01-01T00:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void systemDefaultIsCreatedOnlyOnce() {
        SpendingLimit first = createSystemDefault("2022-01-05T00:00:00Z");
        SpendingLimit second = createSystemDefault("2022-03-01T00:00:00Z");

        assertThat(second).isEqualTo(first);
        assertThat(first.setAt()).isEqualTo(Instant.parse("2022-01-05T00:00:00Z"));
        assertThat(first.amount()).isEqualTo(Money.usd("1000"));
    }

    @Test
    void concurrentCallersAllGetTheSameSystemDefault() throws Exception {
        List<Callable<SpendingLimit>> callers = IntStream.range(0, 8)
                .<Callable<SpendingLimit>>mapToObj(i -> () -> createSystemDefault("2022-01-01T00:00:0%dZ".formatted(i)))
                .toList();

        List<SpendingLimit> results;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            results = executor.invokeAll(callers).stream().map(SpendingLimitRepositoryIT::get).toList();
        }

        assertThat(results).extracting(SpendingLimit::id).containsOnly(results.getFirst().id());
        assertThat(jdbc.sql("select count(*) from spending_limit where account = ? and is_default")
                .param(account.value()).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void storedLimitsCannotBeChangedEvenBypassingTheApplication() {
        SpendingLimit saved = addClientLimit("1000", "2022-01-01T00:00:00Z");

        assertThatThrownBy(() -> jdbc.sql("update spending_limit set limit_sum = 1 where id = ?").param(saved.id()).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.sql("delete from spending_limit where id = ?").param(saved.id()).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }

    private SpendingLimit addClientLimit(String usd, String setAt) {
        return repository.add(SpendingLimit.setByClient(account, PRODUCT, Money.usd(usd), Instant.parse(setAt)));
    }

    private SpendingLimit createSystemDefault(String at) {
        return repository.findOrCreateSystemDefault(
                SpendingLimit.systemDefault(account, PRODUCT, Money.usd("1000"), Instant.parse(at)));
    }

    private Optional<SpendingLimit> findLatest(String moment) {
        return repository.findLatestClientLimit(account, PRODUCT, Instant.parse(moment));
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
