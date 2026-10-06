package com.evlarus.spendinglimit.limit.domain;

import static com.evlarus.spendinglimit.support.DomainFixtures.CLIENT;
import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.DomainFixtures.clientLimit;
import static com.evlarus.spendinglimit.support.DomainFixtures.instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import org.junit.jupiter.api.Test;

class SpendingLimitTest {

    @Test
    void clientLimitIsNotSystemDefaultAndNotSavedYet() {
        SpendingLimit limit = SpendingLimit.setByClient(
                CLIENT, ExpenseCategory.SERVICE, Money.usd("2000"), instant("2022-01-10T00:00:00Z"));

        assertThat(limit.systemDefault()).isFalse();
        assertThat(limit.isPersisted()).isFalse();
        assertThat(limit.setAt()).isEqualTo(instant("2022-01-10T00:00:00Z"));
    }

    @Test
    void limitWithIdIsPersisted() {
        assertThat(clientLimit(1, "1000", "2022-01-01T00:00:00Z").isPersisted()).isTrue();
    }

    @Test
    void systemDefaultIsMarkedAsSuch() {
        SpendingLimit limit = SpendingLimit.systemDefault(
                CLIENT, ExpenseCategory.PRODUCT, Money.usd("1000"), instant("2022-01-01T00:00:00Z"));

        assertThat(limit.systemDefault()).isTrue();
    }

    @Test
    void mustBeInUsd() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpendingLimit.setByClient(
                        CLIENT, ExpenseCategory.PRODUCT, Money.of("1000", KZT), instant("2022-01-01T00:00:00Z")))
                .withMessageContaining("USD");
    }

    @Test
    void mustNotBeNegativeButMayBeZero() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpendingLimit.setByClient(
                        CLIENT, ExpenseCategory.PRODUCT, Money.usd("-1"), instant("2022-01-01T00:00:00Z")));

        assertThat(SpendingLimit.setByClient(
                CLIENT, ExpenseCategory.PRODUCT, Money.usd("0"), instant("2022-01-01T00:00:00Z")).amount())
                .isEqualTo(Money.usd("0"));
    }

    @Test
    void storesSetAtWithMicrosecondPrecision() {
        SpendingLimit limit = SpendingLimit.setByClient(
                CLIENT, ExpenseCategory.PRODUCT, Money.usd("1"), instant("2022-01-01T00:00:00.123456789Z"));

        assertThat(limit.setAt()).isEqualTo(instant("2022-01-01T00:00:00.123456Z"));
    }
}
