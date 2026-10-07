package com.evlarus.spendinglimit.limit.api;

import static com.evlarus.spendinglimit.support.DomainFixtures.COUNTERPARTY;
import static com.evlarus.spendinglimit.support.DomainFixtures.UTC_CALENDAR;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.application.LimitService;
import com.evlarus.spendinglimit.limit.application.LimitUsageQuery;
import com.evlarus.spendinglimit.limit.domain.LimitAlreadySetException;
import com.evlarus.spendinglimit.limit.domain.MonthlySpending;
import com.evlarus.spendinglimit.limit.domain.MonthlySpendingRepository;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.limit.domain.SpendingLimitRepository;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.evlarus.spendinglimit.support.TestAccounts;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class LimitApiIT {

    private static final String LIMITS = "/api/client/v1/limits";

    @Autowired
    private RestTestClient client;

    @Autowired
    private SpendingLimitRepository limits;

    @Autowired
    private MonthlySpendingRepository spendings;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private LimitUsageQuery usageQuery;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final AccountNumber account = TestAccounts.unique();

    @Test
    void setLimitIsListedWithoutUsage() {
        client.post().uri(LIMITS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"account": "%s", "expense_category": "service", "limit_sum": 2500.50}
                        """.formatted(account.value()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").exists()
                .jsonPath("$.limit_datetime").exists()
                .jsonPath("$.limit_currency_shortname").isEqualTo("USD");

        client.get().uri(LIMITS + "?account={account}", account.value())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].expense_category").isEqualTo("service")
                .jsonPath("$[0].limit_sum").isEqualTo(2500.50)
                .jsonPath("$[0].system_default").isEqualTo(false)
                .jsonPath("$[0].checked_transactions").isEqualTo(0);
    }

    @Test
    void listShowsTheUsageOfEveryLimitInASingleQuery() {
        SpendingLimit limit100 = limits.add(SpendingLimit.setByClient(
                account, ExpenseCategory.PRODUCT, Money.usd("100"), Instant.parse("2022-01-01T00:00:00Z")));
        limits.findOrCreateSystemDefault(SpendingLimit.systemDefault(
                account, ExpenseCategory.SERVICE, Money.usd("1000"), Instant.parse("2022-01-01T00:00:00Z")));
        processUsd("45", "2022-01-02T12:00:00Z", limit100);
        processUsd("45", "2022-01-03T12:00:00Z", limit100);
        processUsd("45", "2022-01-04T12:00:00Z", limit100);

        client.get().uri(LIMITS + "?account={account}", account.value())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].expense_category").isEqualTo("product")
                .jsonPath("$[0].checked_transactions").isEqualTo(3)
                .jsonPath("$[0].exceeded_transactions").isEqualTo(1)
                .jsonPath("$[0].checked_sum_usd").isEqualTo(135.00)
                .jsonPath("$[1].expense_category").isEqualTo("service")
                .jsonPath("$[1].system_default").isEqualTo(true)
                .jsonPath("$[1].checked_transactions").isEqualTo(0);
    }

    @Test
    void accountWithoutLimitsHasAnEmptyList() {
        client.get().uri(LIMITS + "?account={account}", account.value())
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void secondLimitAtTheSameMomentIsReportedAsAConflict() {
        LimitService frozenTime = new LimitService(
                limits, usageQuery, Clock.fixed(Instant.parse("2022-01-10T00:00:00Z"), ZoneOffset.UTC));
        frozenTime.setLimit(account, ExpenseCategory.PRODUCT, Money.usd("1000"));

        assertThatThrownBy(() -> frozenTime.setLimit(account, ExpenseCategory.PRODUCT, Money.usd("2000")))
                .isInstanceOf(LimitAlreadySetException.class);
    }

    private void processUsd(String usd, String at, SpendingLimit limit) {
        OffsetDateTime occurredAt = OffsetDateTime.parse(at);
        Instant now = occurredAt.toInstant().plusSeconds(1);
        long id = transactions.add(Transaction.receive(account, COUNTERPARTY, Money.usd(usd),
                ExpenseCategory.PRODUCT, occurredAt, now, Duration.ZERO)).id();
        transactionTemplate.executeWithoutResult(status -> {
            Transaction transaction = transactions.lockById(id).orElseThrow();
            MonthlySpending spending = spendings.lockOrCreate(
                    account, ExpenseCategory.PRODUCT, transaction.businessMonth(UTC_CALENDAR));
            transaction.process(ExchangeRate.usd(transaction.businessDate(UTC_CALENDAR)), spending, limit, UTC_CALENDAR, now);
            spendings.save(spending);
            transactions.saveProcessingResult(transaction);
        });
    }
}
