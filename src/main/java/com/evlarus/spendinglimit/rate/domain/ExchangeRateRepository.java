package com.evlarus.spendinglimit.rate.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Currency;
import java.util.Optional;

public interface ExchangeRateRepository {

    Optional<ExchangeRate> find(Currency currency, LocalDate date);

    /**
     * Stores rates that are not stored yet. A rate already stored for the same currency and day is kept:
     * calculations already made with it stay reproducible.
     */
    void addAllIfAbsent(Collection<ExchangeRate> rates);
}
