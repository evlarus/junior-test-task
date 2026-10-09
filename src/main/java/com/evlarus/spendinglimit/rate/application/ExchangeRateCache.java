package com.evlarus.spendinglimit.rate.application;

import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;

public interface ExchangeRateCache {

    Optional<ExchangeRate> get(Currency currency, LocalDate date);

    void put(ExchangeRate rate);
}
