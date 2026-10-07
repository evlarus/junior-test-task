package com.evlarus.spendinglimit.transaction;

import static com.evlarus.spendinglimit.support.DomainFixtures.COUNTERPARTY;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.stubCloses;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.timeSeriesRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.limit.domain.SpendingLimitRepository;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.evlarus.spendinglimit.support.QueryCounter;
import com.evlarus.spendinglimit.support.TestAccounts;
import com.evlarus.spendinglimit.transaction.application.ExceededTransactionsQuery;
import com.evlarus.spendinglimit.transaction.application.PendingTransactionRetrier;
import com.evlarus.spendinglimit.transaction.application.TransactionProcessor;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionRepository;
import com.evlarus.spendinglimit.transaction.domain.TransactionStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
class TransactionProcessingIT {

    private static final String TRANSACTIONS = "/api/integration/v1/transactions";
    private static final String EXCEEDED = "/api/client/v1/transactions/limit-exceeded";

    @Autowired
    private RestTestClient client;

    @Autowired
    private WireMockServer twelveDataMock;

    @Autowired
    private SpendingLimitRepository limits;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private TransactionProcessor processor;

    @Autowired
    private PendingTransactionRetrier retrier;

    @Autowired
    private ExceededTransactionsQuery exceededTransactions;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcClient jdbc;

    private final AccountNumber account = TestAccounts.unique();

    @BeforeEach
    void resetMock() {
        twelveDataMock.resetAll();
    }

    @Test
    void transactionInTengeIsConvertedAndCheckedAgainstTheLimit() {
        setLimit("1000", "2023-03-01T00:00:00Z");
        stubCloses(twelveDataMock, "KZT", Map.of(LocalDate.of(2023, 3, 6), "450.00"));

        receive("KZT", "225000.00", "2023-03-06T12:00:00+06:00")
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("processed")
                .jsonPath("$.sum_usd").isEqualTo(500.00)
                .jsonPath("$.limit_exceeded").isEqualTo(false);
        receive("KZT", "270000.00", "2023-03-06T13:00:00+06:00")
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sum_usd").isEqualTo(600.00)
                .jsonPath("$.limit_exceeded").isEqualTo(true);
    }

    @Test
    void withoutAClientLimitTheDefaultOf1000UsdApplies() {
        receive("USD", "1000.00", "2023-02-10T10:00:00Z").expectStatus().isCreated()
                .expectBody().jsonPath("$.limit_exceeded").isEqualTo(false);
        receive("USD", "0.01", "2023-02-10T11:00:00Z").expectStatus().isCreated()
                .expectBody().jsonPath("$.limit_exceeded").isEqualTo(true);

        assertThat(jdbc.sql("select limit_sum from spending_limit where account = ? and is_default")
                .param(account.value()).query(BigDecimal.class).single()).isEqualByComparingTo("1000");
        client.get().uri(EXCEEDED + "?account={account}", account.value())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].datetime").isEqualTo("2023-02-10T11:00:00Z")
                .jsonPath("$[0].limit_sum").isEqualTo(1000.00)
                .jsonPath("$[0].limit_datetime").isEqualTo("2023-02-10T10:00:00Z");
    }

    @Test
    void concurrentTransactionsNeverSeeTheSameRemainder() throws Exception {
        setLimit("1000", "2023-01-01T00:00:00Z");
        List<Callable<Integer>> requests = IntStream.range(0, 20)
                .<Callable<Integer>>mapToObj(i -> () -> receive("USD", "100.00", "2023-01-15T10:00:%02dZ".formatted(i))
                        .expectStatus().isCreated()
                        .returnResult(String.class).getStatus().value())
                .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Integer> response : executor.invokeAll(requests)) {
                assertThat(response.get()).isEqualTo(201);
            }
        }

        assertThat(countFlagged()).as("10 x 100 fit into 1000, the other 10 exceed it").isEqualTo(10);
        assertThat(jdbc.sql("select spent_usd from monthly_spending where account = ?")
                .param(account.value()).query(BigDecimal.class).single()).isEqualByComparingTo("2000");
    }

    @Test
    void pendingTransactionProcessedConcurrentlyIsCountedOnce() throws Exception {
        setLimit("1000", "2023-01-01T00:00:00Z");
        long id = transactions.add(Transaction.receive(account, COUNTERPARTY, Money.usd("300"), ExpenseCategory.PRODUCT,
                OffsetDateTime.parse("2023-01-20T10:00:00Z"), Instant.now(), Duration.ZERO)).id();
        ExchangeRate usd = ExchangeRate.usd(LocalDate.of(2023, 1, 20));
        List<Callable<Optional<Transaction>>> processors = IntStream.range(0, 4)
                .<Callable<Optional<Transaction>>>mapToObj(i -> () -> processor.process(id, usd))
                .toList();

        List<Optional<Transaction>> results;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            results = executor.invokeAll(processors).stream().map(TransactionProcessingIT::get).toList();
        }

        assertThat(results).filteredOn(Optional::isPresent).hasSize(1);
        assertThat(jdbc.sql("select spent_usd from monthly_spending where account = ?")
                .param(account.value()).query(BigDecimal.class).single()).isEqualByComparingTo("300");
    }

    @Test
    void transactionStaysPendingWhileTheRateIsUnavailableAndIsProcessedLater() {
        setLimit("1000", "2023-04-01T00:00:00Z");
        twelveDataMock.stubFor(timeSeriesRequest("KZT").willReturn(status(503)));

        receive("KZT", "45000.00", "2023-04-10T12:00:00+06:00")
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.status").isEqualTo("pending")
                .jsonPath("$.limit_exceeded").isEmpty();
        long id = latestTransactionId();

        assertThat(jdbc.sql("select attempts from bank_transaction where id = ?").param(id).query(Integer.class).single())
                .isEqualTo(1);

        twelveDataMock.resetAll();
        stubCloses(twelveDataMock, "KZT", Map.of(LocalDate.of(2023, 4, 10), "450.00"));
        retrier.retryDue();

        Transaction processed = transactions.findById(id).orElseThrow();
        assertThat(processed.status()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(processed.result().orElseThrow().amountUsd()).isEqualTo(Money.usd("100"));
    }

    @Test
    void exceededTransactionsAreReportedWithTheLimitTheyExceededInOneQuery() {
        setLimit("1000", "2023-05-01T00:00:00Z");
        receive("USD", "600.00", "2023-05-02T12:00:00+06:00").expectStatus().isCreated();
        receive("USD", "600.00", "2023-05-03T12:00:00+06:00").expectStatus().isCreated();
        setLimit("2000", "2023-05-10T00:00:00Z");
        receive("USD", "100.00", "2023-05-11T12:00:00+06:00").expectStatus().isCreated();

        client.get().uri(EXCEEDED + "?account={account}", account.value())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].datetime").isEqualTo("2023-05-03T12:00:00+06:00")
                .jsonPath("$[0].sum").isEqualTo(600.00)
                .jsonPath("$[0].currency_shortname").isEqualTo("USD")
                .jsonPath("$[0].limit_sum").isEqualTo(1000.00)
                .jsonPath("$[0].limit_datetime").isEqualTo("2023-05-01T00:00:00Z")
                .jsonPath("$[0].limit_currency_shortname").isEqualTo("USD");

        QueryCounter queries = new QueryCounter(entityManagerFactory);
        queries.reset();
        assertThat(exceededTransactions.findByAccount(account, 0, 100)).hasSize(1);
        assertThat(queries.statements()).isEqualTo(1);
    }

    @Test
    void unsupportedCurrencyAndFutureTransactionsAreRejected() {
        receive("EUR", "100.00", "2023-01-10T10:00:00Z").expectStatus().isEqualTo(422);
        receive("USD", "100.00", OffsetDateTime.now().plusDays(1).toString()).expectStatus().isEqualTo(422);

        assertThat(jdbc.sql("select count(*) from bank_transaction where account_from = ?")
                .param(account.value()).query(Long.class).single()).isZero();
    }

    private RestTestClient.ResponseSpec receive(String currency, String sum, String datetime) {
        return client.post().uri(TRANSACTIONS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"account_from": "%s", "account_to": "9999999999", "currency_shortname": "%s",
                         "sum": %s, "expense_category": "product", "datetime": "%s"}
                        """.formatted(account.value(), currency, sum, datetime))
                .exchange();
    }

    private void setLimit(String usd, String setAt) {
        limits.add(SpendingLimit.setByClient(account, ExpenseCategory.PRODUCT, Money.usd(usd), Instant.parse(setAt)));
    }

    private long countFlagged() {
        return jdbc.sql("select count(*) from bank_transaction where account_from = ? and limit_exceeded")
                .param(account.value()).query(Long.class).single();
    }

    private long latestTransactionId() {
        return jdbc.sql("select max(id) from bank_transaction where account_from = ?")
                .param(account.value()).query(Long.class).single();
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
