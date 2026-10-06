package com.evlarus.spendinglimit.rate.domain;

import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.DomainFixtures.RUB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.evlarus.spendinglimit.common.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExchangeRateTest {

    private static final LocalDate FRIDAY = LocalDate.of(2021, 12, 31);
    private static final LocalDate SATURDAY = LocalDate.of(2022, 1, 1);
    private static final LocalDate SUNDAY = LocalDate.of(2022, 1, 2);
    private static final LocalDate MONDAY = LocalDate.of(2022, 1, 3);

    private static ExchangeRate kztClose(String unitsPerUsd) {
        return new ExchangeRate(KZT, MONDAY, new BigDecimal(unitsPerUsd), MONDAY, RateKind.CLOSE);
    }

    @Nested
    class Conversion {

        @Test
        void dividesByUnitsPerUsd() {
            assertThat(kztClose("450").toUsd(Money.of("225000.00", KZT))).isEqualTo(Money.usd("500.00"));
        }

        @ParameterizedTest(name = "{0} KZT at 4 per USD = {1} USD")
        @CsvSource({
            "0.10, 0.02", // 0.025 -> 0.02: half to even, HALF_UP would give 0.03
            "0.30, 0.08", // 0.075 -> 0.08
            "1.00, 0.25"
        })
        void roundsOnceToCentsHalfEven(String kzt, String expectedUsd) {
            assertThat(kztClose("4").toUsd(Money.of(kzt, KZT))).isEqualTo(Money.usd(expectedUsd));
        }

        @Test
        void tinyAmountBecomesZeroUsd() {
            assertThat(kztClose("450").toUsd(Money.of("1.00", KZT))).isEqualTo(Money.usd("0.00"));
        }

        @Test
        void usdIsNotConverted() {
            Money amount = Money.usd("123.45");

            assertThat(ExchangeRate.usd(MONDAY).toUsd(amount)).isEqualTo(amount);
        }

        @Test
        void refusesAmountInAnotherCurrency() {
            assertThatIllegalArgumentException().isThrownBy(() -> kztClose("450").toUsd(Money.of("100", RUB)));
        }
    }

    @Nested
    class Selection {

        private final List<DailyClose> closes = List.of(
                new DailyClose(KZT, FRIDAY, new BigDecimal("431.80")),
                new DailyClose(KZT, MONDAY, new BigDecimal("433.10")),
                new DailyClose(RUB, SUNDAY, new BigDecimal("74.50")));

        @Test
        void usesCloseOfThatDayWhenThereWasTrading() {
            ExchangeRate rate = ExchangeRate.select(KZT, MONDAY, closes).orElseThrow();

            assertThat(rate.kind()).isEqualTo(RateKind.CLOSE);
            assertThat(rate.unitsPerUsd()).isEqualByComparingTo("433.10");
            assertThat(rate.sourceDate()).isEqualTo(MONDAY);
        }

        @Test
        void usesPreviousCloseOnAWeekend() {
            ExchangeRate rate = ExchangeRate.select(KZT, SUNDAY, closes).orElseThrow();

            assertThat(rate.kind()).isEqualTo(RateKind.PREVIOUS_CLOSE);
            assertThat(rate.rateDate()).isEqualTo(SUNDAY);
            assertThat(rate.sourceDate()).isEqualTo(FRIDAY);
            assertThat(rate.unitsPerUsd()).isEqualByComparingTo("431.80");
        }

        @Test
        void ignoresClosesAfterTheDayAndOfOtherCurrencies() {
            ExchangeRate rate = ExchangeRate.select(KZT, SATURDAY, closes).orElseThrow();

            assertThat(rate.sourceDate()).isEqualTo(FRIDAY);
        }

        @Test
        void isEmptyWhenNoCloseOnOrBeforeTheDay() {
            assertThat(ExchangeRate.select(KZT, FRIDAY.minusDays(1), closes)).isEmpty();
            assertThat(ExchangeRate.select(RUB, SATURDAY, closes)).isEmpty();
        }
    }

    @Nested
    class Invariants {

        @Test
        void closeMustComeFromTheSameDay() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new ExchangeRate(KZT, MONDAY, BigDecimal.TEN, FRIDAY, RateKind.CLOSE));
        }

        @Test
        void previousCloseMustComeFromAnEarlierDay() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new ExchangeRate(KZT, MONDAY, BigDecimal.TEN, MONDAY, RateKind.PREVIOUS_CLOSE));
        }

        @Test
        void rateMustBePositive() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new ExchangeRate(KZT, MONDAY, BigDecimal.ZERO, MONDAY, RateKind.CLOSE));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new DailyClose(KZT, MONDAY, new BigDecimal("-1")));
        }

        @Test
        void rateIsStoredWithEightDecimals() {
            assertThat(new DailyClose(KZT, MONDAY, new BigDecimal("450.123456789")).unitsPerUsd())
                    .isEqualTo(new BigDecimal("450.12345679"));
        }
    }
}
