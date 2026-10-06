package com.evlarus.spendinglimit.limit.domain;

import static com.evlarus.spendinglimit.support.DomainFixtures.CLIENT;
import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.DomainFixtures.OTHER_CLIENT;
import static com.evlarus.spendinglimit.support.DomainFixtures.clientLimit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class MonthlySpendingTest {

    private static final YearMonth JANUARY = YearMonth.of(2022, 1);
    private static final SpendingLimit LIMIT_1000 = clientLimit(1, "1000", "2022-01-01T00:00:00Z");

    private final MonthlySpending spending = MonthlySpending.startOf(CLIENT, ExpenseCategory.PRODUCT, JANUARY);

    @Test
    void startsWithNothingSpent() {
        assertThat(spending.spent()).isEqualTo(Money.usd("0"));
    }

    @Test
    void accumulatesSpendingAndReportsRemainder() {
        spending.register(Money.usd("500"), LIMIT_1000);
        LimitCheck check = spending.register(Money.usd("300"), LIMIT_1000);

        assertThat(spending.spent()).isEqualTo(Money.usd("800"));
        assertThat(check.remaining()).isEqualTo(Money.usd("200"));
        assertThat(check.exceeded()).isFalse();
    }

    @Test
    void spendingExactlyTheWholeLimitIsNotExceeding() {
        spending.register(Money.usd("999.99"), LIMIT_1000);

        LimitCheck check = spending.register(Money.usd("0.01"), LIMIT_1000);

        assertThat(check.remaining()).isEqualTo(Money.usd("0"));
        assertThat(check.exceeded()).isFalse();
    }

    @Test
    void oneCentOverTheLimitIsExceeding() {
        spending.register(Money.usd("1000"), LIMIT_1000);

        LimitCheck check = spending.register(Money.usd("0.01"), LIMIT_1000);

        assertThat(check.remaining()).isEqualTo(Money.usd("-0.01"));
        assertThat(check.exceeded()).isTrue();
    }

    @Test
    void spendingBeforeANewLimitStillCountsInTheSameMonth() {
        spending.register(Money.usd("1100"), LIMIT_1000);
        SpendingLimit limit2000 = clientLimit(2, "2000", "2022-01-10T00:00:00Z");

        LimitCheck check = spending.register(Money.usd("100"), limit2000);

        assertThat(check.remaining()).isEqualTo(Money.usd("800"));
        assertThat(check.exceeded()).isFalse();
    }

    @Test
    void zeroLimitIsExceededByAnyPositiveAmount() {
        SpendingLimit zeroLimit = clientLimit(3, "0", "2022-01-01T00:00:00Z");

        assertThat(spending.register(Money.usd("0"), zeroLimit).exceeded()).isFalse();
        assertThat(spending.register(Money.usd("0.01"), zeroLimit).exceeded()).isTrue();
    }

    @Test
    void acceptsZeroUsdForTinyAmounts() {
        // 1 KZT is 0.002 USD and rounds to 0.00
        LimitCheck check = spending.register(Money.usd("0"), LIMIT_1000);

        assertThat(check.remaining()).isEqualTo(Money.usd("1000"));
    }

    @Test
    void rejectsAmountsNotInUsdOrNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> spending.register(Money.of("100", KZT), LIMIT_1000));
        assertThatIllegalArgumentException().isThrownBy(() -> spending.register(Money.usd("-1"), LIMIT_1000));
        assertThat(spending.spent()).isEqualTo(Money.usd("0"));
    }

    @Test
    void rejectsLimitOfAnotherAccountOrCategory() {
        SpendingLimit otherAccount = new SpendingLimit(
                4L, OTHER_CLIENT, ExpenseCategory.PRODUCT, Money.usd("1000"), LIMIT_1000.setAt(), false);
        SpendingLimit otherCategory = new SpendingLimit(
                5L, CLIENT, ExpenseCategory.SERVICE, Money.usd("1000"), LIMIT_1000.setAt(), false);

        assertThatIllegalArgumentException().isThrownBy(() -> spending.register(Money.usd("1"), otherAccount));
        assertThatIllegalArgumentException().isThrownBy(() -> spending.register(Money.usd("1"), otherCategory));
        assertThat(spending.spent()).isEqualTo(Money.usd("0"));
    }

    @Test
    void cannotBeRestoredWithNegativeOrNonUsdTotal() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new MonthlySpending(CLIENT, ExpenseCategory.PRODUCT, JANUARY, Money.usd("-1")));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new MonthlySpending(CLIENT, ExpenseCategory.PRODUCT, JANUARY, Money.of("1", KZT)));
    }
}
