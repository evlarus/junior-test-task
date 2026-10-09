package com.evlarus.spendinglimit.rate.infrastructure.cache;

import com.evlarus.spendinglimit.rate.application.ExchangeRateCache;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rates.cache.type", havingValue = "none", matchIfMissing = true)
class NoExchangeRateCache implements ExchangeRateCache {

    @Override
    public Optional<ExchangeRate> get(Currency currency, LocalDate date) {
        return Optional.empty();
    }

    @Override
    public void put(ExchangeRate rate) {
    }
}
