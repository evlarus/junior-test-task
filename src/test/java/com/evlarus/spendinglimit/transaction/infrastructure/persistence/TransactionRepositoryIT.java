package com.evlarus.spendinglimit.transaction.infrastructure.persistence;

import static com.evlarus.spendinglimit.common.domain.ExpenseCategory.PRODUCT;
import static com.evlarus.spendinglimit.support.DomainFixtures.COUNTERPARTY;
import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.DomainFixtures.UTC_CALENDAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.MonthlySpending;
import com.evlarus.spendinglimit.limit.domain.MonthlySpendingRepository;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.limit.domain.SpendingLimitRepository;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.rate.domain.RateKind;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.evlarus.spendinglimit.support.QueryCounter;
import com.evlarus.spendinglimit.support.TestAccounts;
import com.evlarus.spendinglimit.transaction.domain.ProcessingResult;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionRepository;
import com.evlarus.spendinglimit.transaction.domain.TransactionStatus;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class TransactionRepositoryIT {

    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2022-01-03T12:00:00.123456+06:00");
    private static final Instant NOW = Instant.parse("2022-01-03T06:00:01Z");
    private static final LocalDate DAY = LocalDate.of(2022, 1, 3);
    private static final ExchangeRate KZT_RATE = new ExchangeRate(KZT, DAY, new BigDecimal("450"), DAY, RateKind.CLOSE);

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private SpendingLimitRepository limits;

    @Autowired
    private MonthlySpendingRepository spendings;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcClient jdbc;

    private final AccountNumber account = TestAccounts.unique();

    @Test
    void receivedTransactionIsReadBackUnchangedWithTheClientOffset() {
        Transaction saved = transactions.add(received());

        Transaction loaded = transactions.findById(saved.id()).orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(saved);
        assertThat(loaded.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(loaded.status()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void processingResultIsStoredWithTheAppliedLimit() {
        SpendingLimit limit = clientLimit();
        long id = transactions.add(received()).id();

        process(id, limit);

        Transaction loaded = transactions.findById(id).orElseThrow();
        assertThat(loaded.status()).isEqualTo(TransactionStatus.PROCESSED);
        // 10000.45 KZT / 450 = 22.2232 -> 22.22 USD
        assertThat(loaded.result()).contains(new ProcessingResult(Money.usd("22.22"), limit.id(), false, NOW));
    }

    @Test
    void readingAProcessedTransactionIsASingleQuery() {
        long id = transactions.add(received()).id();
        process(id, clientLimit());
        QueryCounter queries = new QueryCounter(entityManagerFactory);
        queries.reset();

        transactions.findById(id).orElseThrow();

        assertThat(queries.statements()).as("the limit must not be lazy-loaded").isEqualTo(1);
    }

    @Test
    void onlyNewPendingTransactionsCanBeAdded() {
        Transaction saved = transactions.add(received());

        assertThatThrownBy(() -> transactions.add(saved))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("new pending");
    }

    @Test
    void lockingOutsideOfATransactionIsRefused() {
        long id = transactions.add(received()).id();

        assertThatThrownBy(() -> transactions.lockById(id)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void resultOfATransactionThatWasNotLockedIsNotSaved() {
        Transaction saved = transactions.add(received());
        Transaction processedElsewhere = new Transaction(
                saved.id(), saved.accountFrom(), saved.accountTo(), saved.amount(), saved.category(),
                saved.occurredAt(), saved.receivedAt(), new ProcessingResult(Money.usd("1"), clientLimit().id(), false, NOW));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> transactions.saveProcessingResult(processedElsewhere)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("must be locked");
        assertThat(transactions.findById(saved.id()).orElseThrow().isPending()).isTrue();
    }

    @Test
    void databaseRejectsAProcessedTransactionWithoutItsResult() {
        assertThatThrownBy(() -> jdbc.sql("""
                insert into bank_transaction (account_from, account_to, currency, amount, expense_category,
                    occurred_at, occurred_offset_seconds, received_at, status)
                values (?, '9999999999', 'KZT', 1, 'PRODUCT', now(), 0, now(), 'PROCESSED')
                """).param(account.value()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Transaction received() {
        return Transaction.receive(
                account, COUNTERPARTY, Money.of("10000.45", KZT), PRODUCT, OCCURRED_AT, NOW, Duration.ofMinutes(5));
    }

    private SpendingLimit clientLimit() {
        return limits.add(SpendingLimit.setByClient(account, PRODUCT, Money.usd("1000"), Instant.parse("2022-01-01T00:00:00Z")));
    }

    private void process(long id, SpendingLimit limit) {
        transactionTemplate.executeWithoutResult(status -> {
            Transaction transaction = transactions.lockById(id).orElseThrow();
            MonthlySpending spending = spendings.lockOrCreate(
                    account, transaction.category(), transaction.businessMonth(UTC_CALENDAR));
            transaction.process(KZT_RATE, spending, limit, UTC_CALENDAR, NOW);
            spendings.save(spending);
            transactions.saveProcessingResult(transaction);
        });
    }
}
