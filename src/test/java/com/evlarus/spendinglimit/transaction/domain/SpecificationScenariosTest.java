package com.evlarus.spendinglimit.transaction.domain;

import static com.evlarus.spendinglimit.support.DomainFixtures.CLIENT;
import static com.evlarus.spendinglimit.support.DomainFixtures.COUNTERPARTY;
import static com.evlarus.spendinglimit.support.DomainFixtures.UTC_CALENDAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.LimitCheck;
import com.evlarus.spendinglimit.limit.domain.MonthlySpending;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Both cases of the table from the specification, row by row, through the real domain objects.
 * Amounts are in USD so that the numbers match the table exactly; conversion is covered elsewhere.
 */
class SpecificationScenariosTest {

    private final Scenario scenario = new Scenario();

    @Test
    void case1_newLimitInTheMiddleOfTheMonthDoesNotAffectEarlierTransactions() {
        scenario.limitSet("1000", "2022-01-01T00:00:00Z");
        scenario.transaction("500", "2022-01-02T12:00:00Z").leaves("500").exceeded(false);
        scenario.transaction("600", "2022-01-03T12:00:00Z").leaves("-100").exceeded(true);

        scenario.limitSet("2000", "2022-01-10T00:00:00Z"); // 2000 - 1100 already spent = 900
        scenario.transaction("100", "2022-01-11T12:00:00Z").leaves("800").exceeded(false);
        scenario.transaction("700", "2022-01-12T12:00:00Z").leaves("100").exceeded(false);
        scenario.transaction("100", "2022-01-13T12:00:00Z").leaves("0").exceeded(false);
        scenario.transaction("100", "2022-01-13T13:00:00Z").leaves("-100").exceeded(true);
    }

    @Test
    void case2_newLowerLimitMakesFollowingTransactionsExceed() {
        scenario.limitSet("1000", "2022-02-01T00:00:00Z");
        scenario.transaction("500", "2022-02-02T12:00:00Z").leaves("500").exceeded(false);
        scenario.transaction("100", "2022-02-03T12:00:00Z").leaves("400").exceeded(false);

        scenario.limitSet("400", "2022-02-10T00:00:00Z"); // 400 - 600 already spent = -200
        scenario.transaction("100", "2022-02-11T12:00:00Z").leaves("-300").exceeded(true);
        scenario.transaction("100", "2022-02-12T12:00:00Z").leaves("-400").exceeded(true);
    }

    @Test
    void newMonthStartsFromZeroWithTheLatestLimit() {
        scenario.limitSet("1000", "2022-01-01T00:00:00Z");
        scenario.transaction("900", "2022-01-31T23:59:59Z").leaves("100").exceeded(false);
        scenario.transaction("900", "2022-02-01T00:00:00Z").leaves("100").exceeded(false);
        scenario.transaction("200", "2022-02-01T00:00:01Z").leaves("-100").exceeded(true);
    }

    /** Plays transactions in time order, like they arrive in real time. */
    private static final class Scenario {

        private final Map<YearMonth, MonthlySpending> spendingByMonth = new HashMap<>();
        private SpendingLimit currentLimit;
        private long nextLimitId = 1;
        private long nextTransactionId = 1;

        void limitSet(String usd, String at) {
            currentLimit = new SpendingLimit(
                    nextLimitId++, CLIENT, ExpenseCategory.PRODUCT, Money.usd(usd), Instant.parse(at), false);
        }

        Outcome transaction(String usd, String at) {
            OffsetDateTime occurredAt = OffsetDateTime.parse(at);
            Instant now = occurredAt.toInstant().plusSeconds(1);
            Transaction transaction = Transaction.receive(
                    CLIENT, COUNTERPARTY, Money.usd(usd), ExpenseCategory.PRODUCT, occurredAt, now, Duration.ZERO);
            YearMonth month = transaction.businessMonth(UTC_CALENDAR);
            MonthlySpending spending = spendingByMonth.computeIfAbsent(
                    month, key -> MonthlySpending.startOf(CLIENT, ExpenseCategory.PRODUCT, key));

            LimitCheck check = transaction.process(
                    ExchangeRate.usd(transaction.businessDate(UTC_CALENDAR)), spending, currentLimit, UTC_CALENDAR, now);

            ProcessingResult result = transaction.result().orElseThrow();
            assertThat(result.limitId()).as("limit applied to transaction %d", nextTransactionId)
                    .isEqualTo(currentLimit.id());
            nextTransactionId++;
            return new Outcome(check, result);
        }
    }

    private record Outcome(LimitCheck check, ProcessingResult result) {

        Outcome leaves(String remainingUsd) {
            assertThat(check.remaining()).isEqualTo(Money.usd(remainingUsd));
            return this;
        }

        void exceeded(boolean expected) {
            assertThat(check.exceeded()).isEqualTo(expected);
            assertThat(result.limitExceeded()).isEqualTo(expected);
        }
    }
}
