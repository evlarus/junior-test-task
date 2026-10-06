package com.evlarus.spendinglimit.common.domain;

import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void normalizesScaleToTwoDecimals() {
        Money money = Money.of("10.5", KZT);

        assertThat(money.amount()).isEqualTo(new BigDecimal("10.50"));
        assertThat(money).isEqualTo(Money.of("10.50", KZT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.500", "10", "0.1"})
    void acceptsAmountsThatNeedNoRounding(String amount) {
        assertThat(Money.of(amount, KZT).amount().scale()).isEqualTo(2);
    }

    @Test
    void rejectsAmountThatWouldNeedRounding() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of("10.555", KZT))
                .withMessageContaining("at most 2 decimal places");
    }

    @Test
    void addsAndSubtractsInSameCurrency() {
        Money total = Money.usd("100.10").add(Money.usd("0.90"));

        assertThat(total).isEqualTo(Money.usd("101.00"));
        assertThat(total.subtract(Money.usd("200"))).isEqualTo(Money.usd("-99.00"));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.usd("1").add(Money.of("1", KZT)))
                .withMessageContaining("Currency mismatch");
    }

    @Test
    void zeroIsNeitherPositiveNorNegative() {
        Money zero = Money.zero(Money.USD);

        assertThat(zero.isPositive()).isFalse();
        assertThat(zero.isNegative()).isFalse();
        assertThat(Money.usd("-0.01").isNegative()).isTrue();
        assertThat(Money.usd("0.01").isPositive()).isTrue();
    }

    @Test
    void printsPlainAmountWithCurrencyCode() {
        assertThat(Money.of("1E+3", KZT)).hasToString("1000.00 KZT");
    }
}
