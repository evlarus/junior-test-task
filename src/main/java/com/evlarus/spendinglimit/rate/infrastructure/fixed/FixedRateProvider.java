package com.evlarus.spendinglimit.rate.infrastructure.fixed;

import com.evlarus.spendinglimit.rate.application.ExchangeRateProvider;
import com.evlarus.spendinglimit.rate.application.RateProviderException;
import com.evlarus.spendinglimit.rate.application.RatesProperties;
import com.evlarus.spendinglimit.rate.domain.DailyClose;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Constant rates from configuration ({@code app.rates.fixed.units-per-usd}) on weekdays, for running the
 * service locally or in a demo without an API key. Weekends have no close, like on a real market.
 */
@Component
@ConditionalOnProperty(name = "app.rates.provider", havingValue = "fixed")
class FixedRateProvider implements ExchangeRateProvider {

    private final Map<Currency, BigDecimal> unitsPerUsd;

    FixedRateProvider(RatesProperties properties) {
        this.unitsPerUsd = Map.copyOf(properties.fixed().unitsPerUsd());
    }

    @Override
    public List<DailyClose> dailyCloses(Currency currency, LocalDate from, LocalDate to) {
        BigDecimal rate = unitsPerUsd.get(currency);
        if (rate == null) {
            throw new RateProviderException("No fixed rate is configured for " + currency, null);
        }
        return from.datesUntil(to.plusDays(1))
                .filter(day -> day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY)
                .map(day -> new DailyClose(currency, day, rate))
                .toList();
    }
}
