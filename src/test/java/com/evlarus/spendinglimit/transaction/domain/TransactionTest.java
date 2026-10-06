package com.evlarus.spendinglimit.transaction.domain;

import static com.evlarus.spendinglimit.support.DomainFixtures.CLIENT;
import static com.evlarus.spendinglimit.support.DomainFixtures.COUNTERPARTY;
import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.DomainFixtures.OTHER_CLIENT;
import static com.evlarus.spendinglimit.support.DomainFixtures.UTC_CALENDAR;
import static com.evlarus.spendinglimit.support.DomainFixtures.clientLimit;
import static com.evlarus.spendinglimit.support.DomainFixtures.dateTime;
import static com.evlarus.spendinglimit.support.DomainFixtures.defaultLimit;
import static com.evlarus.spendinglimit.support.DomainFixtures.instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.LimitCheck;
import com.evlarus.spendinglimit.limit.domain.MonthlySpending;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.rate.domain.RateKind;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final Duration SKEW = Duration.ofMinutes(5);
    private static final OffsetDateTime OCCURRED_AT = dateTime("2022-01-03T12:00:00+06:00");
    private static final LocalDate DAY = LocalDate.of(2022, 1, 3);
    private static final YearMonth MONTH = YearMonth.of(2022, 1);
    private static final Instant NOW = instant("2022-01-03T06:00:01Z");
    private static final ExchangeRate KZT_RATE = new ExchangeRate(KZT, DAY, new BigDecimal("450"), DAY, RateKind.CLOSE);
    private static final SpendingLimit LIMIT = clientLimit(7, "1000", "2022-01-01T00:00:00Z");

    private static Transaction receive(String kzt, OffsetDateTime occurredAt, Instant now) {
        return Transaction.receive(
                CLIENT, COUNTERPARTY, Money.of(kzt, KZT), ExpenseCategory.PRODUCT, occurredAt, now, SKEW);
    }

    private static MonthlySpending januarySpending() {
        return MonthlySpending.startOf(CLIENT, ExpenseCategory.PRODUCT, MONTH);
    }

    @Nested
    class Receiving {

        @Test
        void isPendingAndKeepsClientOffset() {
            Transaction transaction = receive("10000.45", OCCURRED_AT, NOW);

            assertThat(transaction.status()).isEqualTo(TransactionStatus.PENDING);
            assertThat(transaction.result()).isEmpty();
            assertThat(transaction.occurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(transaction.occurredAt().getOffset()).isEqualTo(ZoneOffset.ofHours(6));
            assertThat(transaction.receivedAt()).isEqualTo(NOW);
        }

        @Test
        void storesTimeWithMicrosecondPrecision() {
            Transaction transaction = receive("1", dateTime("2022-01-03T12:00:00.123456789+06:00"), NOW);

            assertThat(transaction.occurredAt()).isEqualTo(dateTime("2022-01-03T12:00:00.123456+06:00"));
        }

        @Test
        void acceptsSmallClockDifferenceButNotFutureTransactions() {
            Instant now = OCCURRED_AT.toInstant();

            assertThat(receive("1", OCCURRED_AT.plus(SKEW), now).isPending()).isTrue();
            assertThatThrownBy(() -> receive("1", OCCURRED_AT.plus(SKEW).plusSeconds(1), now))
                    .isInstanceOf(TransactionInFutureException.class);
        }

        @Test
        void amountMustBePositive() {
            assertThatIllegalArgumentException().isThrownBy(() -> receive("0", OCCURRED_AT, NOW));
            assertThatIllegalArgumentException().isThrownBy(() -> receive("-1", OCCURRED_AT, NOW));
        }

        @Test
        void businessDayAndMonthFollowTheBusinessZoneNotTheClientOffset() {
            // 1 February 03:00 in UTC+6 is still 31 January in UTC
            Transaction transaction = receive("1", dateTime("2022-02-01T03:00:00+06:00"), instant("2022-02-01T00:00:00Z"));

            assertThat(transaction.businessDate(UTC_CALENDAR)).isEqualTo(LocalDate.of(2022, 1, 31));
            assertThat(transaction.businessMonth(UTC_CALENDAR)).isEqualTo(YearMonth.of(2022, 1));
        }
    }

    @Nested
    class Processing {

        @Test
        void convertsRegistersAndFixesTheFlag() {
            Transaction transaction = receive("225000.00", OCCURRED_AT, NOW);
            MonthlySpending spending = januarySpending();

            LimitCheck check = transaction.process(KZT_RATE, spending, LIMIT, UTC_CALENDAR, NOW);

            assertThat(check.remaining()).isEqualTo(Money.usd("500"));
            assertThat(spending.spent()).isEqualTo(Money.usd("500"));
            assertThat(transaction.status()).isEqualTo(TransactionStatus.PROCESSED);
            assertThat(transaction.result()).contains(new ProcessingResult(Money.usd("500"), 7, false, NOW));
        }

        @Test
        void isCountedOnlyOnce() {
            Transaction transaction = receive("225000.00", OCCURRED_AT, NOW);
            MonthlySpending spending = januarySpending();
            transaction.process(KZT_RATE, spending, LIMIT, UTC_CALENDAR, NOW);

            assertThatThrownBy(() -> transaction.process(KZT_RATE, spending, LIMIT, UTC_CALENDAR, NOW))
                    .isInstanceOf(TransactionAlreadyProcessedException.class);
            assertThat(spending.spent()).isEqualTo(Money.usd("500"));
        }

        @Test
        void requiresRateOfTheTransactionDay() {
            ExchangeRate nextDayRate = new ExchangeRate(KZT, DAY.plusDays(1), BigDecimal.TEN, DAY.plusDays(1), RateKind.CLOSE);

            assertRejectedWithoutSideEffects(transaction -> transaction.process(
                    nextDayRate, januarySpending(), LIMIT, UTC_CALENDAR, NOW));
        }

        @Test
        void requiresRateOfTheTransactionCurrency() {
            ExchangeRate usdRate = ExchangeRate.usd(DAY);

            assertRejectedWithoutSideEffects(transaction -> transaction.process(
                    usdRate, januarySpending(), LIMIT, UTC_CALENDAR, NOW));
        }

        @Test
        void requiresSpendingOfTheSameAccountCategoryAndMonth() {
            MonthlySpending otherMonth = MonthlySpending.startOf(CLIENT, ExpenseCategory.PRODUCT, MONTH.plusMonths(1));
            MonthlySpending otherCategory = MonthlySpending.startOf(CLIENT, ExpenseCategory.SERVICE, MONTH);
            MonthlySpending otherAccount = MonthlySpending.startOf(OTHER_CLIENT, ExpenseCategory.PRODUCT, MONTH);

            assertRejectedWithoutSideEffects(t -> t.process(KZT_RATE, otherMonth, LIMIT, UTC_CALENDAR, NOW));
            assertRejectedWithoutSideEffects(t -> t.process(KZT_RATE, otherCategory, LIMIT, UTC_CALENDAR, NOW));
            assertRejectedWithoutSideEffects(t -> t.process(KZT_RATE, otherAccount, LIMIT, UTC_CALENDAR, NOW));
        }

        @Test
        void requiresSavedLimit() {
            SpendingLimit unsaved = SpendingLimit.setByClient(
                    CLIENT, ExpenseCategory.PRODUCT, Money.usd("1000"), instant("2022-01-01T00:00:00Z"));

            assertRejectedWithoutSideEffects(t -> t.process(KZT_RATE, januarySpending(), unsaved, UTC_CALENDAR, NOW));
        }

        @Test
        void clientLimitSetAfterTheTransactionCannotApply() {
            SpendingLimit later = clientLimit(8, "1000", "2022-01-03T06:00:00.000001Z");

            assertRejectedWithoutSideEffects(t -> t.process(KZT_RATE, januarySpending(), later, UTC_CALENDAR, NOW));
        }

        @Test
        void systemDefaultMayBeCreatedAfterTheTransaction() {
            Transaction transaction = receive("450", OCCURRED_AT, NOW);
            SpendingLimit createdLater = defaultLimit(9, "2022-01-03T06:00:01Z");

            LimitCheck check = transaction.process(KZT_RATE, januarySpending(), createdLater, UTC_CALENDAR, NOW);

            assertThat(check.remaining()).isEqualTo(Money.usd("999"));
        }

        private void assertRejectedWithoutSideEffects(java.util.function.Consumer<Transaction> processing) {
            Transaction transaction = receive("225000.00", OCCURRED_AT, NOW);

            assertThatIllegalArgumentException().isThrownBy(() -> processing.accept(transaction));
            assertThat(transaction.isPending()).isTrue();
        }
    }

    @Test
    void restoredFromStorageKeepsItsStateAndIsProcessed() {
        ProcessingResult result = new ProcessingResult(Money.usd("0"), 7, false, NOW);

        Transaction transaction = new Transaction(
                1L, CLIENT, COUNTERPARTY, Money.of("1", KZT), ExpenseCategory.SERVICE, OCCURRED_AT, NOW, result);

        assertThat(transaction.id()).isEqualTo(1L);
        assertThat(transaction.accountFrom()).isEqualTo(CLIENT);
        assertThat(transaction.accountTo()).isEqualTo(COUNTERPARTY);
        assertThat(transaction.amount()).isEqualTo(Money.of("1", KZT));
        assertThat(transaction.category()).isEqualTo(ExpenseCategory.SERVICE);
        assertThat(transaction.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(transaction.receivedAt()).isEqualTo(NOW);
        assertThat(transaction.result()).contains(result);
        assertThat(transaction.isPending()).isFalse();
        assertThat(transaction.status()).isEqualTo(TransactionStatus.PROCESSED);
    }

    @Test
    void processingResultIsAlwaysANonNegativeUsdAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProcessingResult(Money.of("1", KZT), 7, false, NOW));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProcessingResult(Money.usd("-1"), 7, false, NOW));
    }
}
