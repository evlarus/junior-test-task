package com.evlarus.spendinglimit.rate.infrastructure.fixed;

import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.DomainFixtures.RUB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evlarus.spendinglimit.rate.application.RateProviderException;
import com.evlarus.spendinglimit.rate.application.RatesProperties;
import com.evlarus.spendinglimit.rate.domain.DailyClose;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FixedRateProviderTest {

    private final FixedRateProvider provider = new FixedRateProvider(new RatesProperties(
            RatesProperties.Provider.FIXED, 10, Set.of(KZT),
            new RatesProperties.TwelveData(null), new RatesProperties.Fixed(Map.of(KZT, new BigDecimal("450")))));

    @Test
    void returnsTheConfiguredRateForWeekdaysOnly() {
        LocalDate friday = LocalDate.of(2022, 1, 7);
        LocalDate monday = LocalDate.of(2022, 1, 10);

        assertThat(provider.dailyCloses(KZT, friday, monday)).containsExactly(
                new DailyClose(KZT, friday, new BigDecimal("450")),
                new DailyClose(KZT, monday, new BigDecimal("450")));
    }

    @Test
    void currencyWithoutAConfiguredRateIsAFailure() {
        assertThatThrownBy(() -> provider.dailyCloses(RUB, LocalDate.of(2022, 1, 7), LocalDate.of(2022, 1, 7)))
                .isInstanceOf(RateProviderException.class);
    }
}
