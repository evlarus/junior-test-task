package com.evlarus.spendinglimit.rate.application;

import com.evlarus.spendinglimit.rate.domain.DailyClose;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

/**
 * External source of exchange rates. Every call may cost money and time, so callers use
 * {@link ExchangeRateService}, which asks the database first.
 *
 * <p>A new provider implements this interface and is selected with {@code app.rates.provider}.
 */
public interface ExchangeRateProvider {

    /**
     * Closing rates of {@code currency} against USD for the trading days between {@code from} and {@code to}
     * inclusive. Weekends and holidays have no close and are simply absent.
     *
     * @throws TransientRateProviderException when the provider is temporarily failing and an immediate retry may help
     * @throws RateProviderException          for any other failure (timeout, rejected request, unreadable response)
     */
    List<DailyClose> dailyCloses(Currency currency, LocalDate from, LocalDate to);
}
