package com.evlarus.spendinglimit.rate.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * Closing rate of one trading day as reported by a rate provider.
 *
 * @param unitsPerUsd how many units of {@code currency} one US dollar costs (about 450 for KZT).
 *                    This direction keeps precision: the inverse for KZT would be about 0.0022.
 */
public record DailyClose(Currency currency, LocalDate date, BigDecimal unitsPerUsd) {

    /** Precision of stored rates. */
    public static final int RATE_SCALE = 8;

    public DailyClose {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(unitsPerUsd, "unitsPerUsd");
        if (unitsPerUsd.signum() <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive, got " + unitsPerUsd);
        }
        unitsPerUsd = unitsPerUsd.setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
    }
}
