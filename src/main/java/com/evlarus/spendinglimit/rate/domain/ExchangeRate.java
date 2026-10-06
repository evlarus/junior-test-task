package com.evlarus.spendinglimit.rate.domain;

import com.evlarus.spendinglimit.common.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;

/**
 * Rate used to convert amounts of one business day to USD.
 *
 * @param rateDate    the day the rate is used for
 * @param unitsPerUsd units of {@code currency} per one US dollar
 * @param sourceDate  the trading day the closing rate was taken from; earlier than {@code rateDate}
 *                    for {@link RateKind#PREVIOUS_CLOSE}
 */
public record ExchangeRate(
        Currency currency, LocalDate rateDate, BigDecimal unitsPerUsd, LocalDate sourceDate, RateKind kind) {

    public ExchangeRate {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(rateDate, "rateDate");
        Objects.requireNonNull(unitsPerUsd, "unitsPerUsd");
        Objects.requireNonNull(sourceDate, "sourceDate");
        Objects.requireNonNull(kind, "kind");
        if (unitsPerUsd.signum() <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive, got " + unitsPerUsd);
        }
        boolean consistent = switch (kind) {
            case CLOSE -> sourceDate.equals(rateDate);
            case PREVIOUS_CLOSE -> sourceDate.isBefore(rateDate);
        };
        if (!consistent) {
            throw new IllegalArgumentException(
                    "%s rate for %s cannot come from %s".formatted(kind, rateDate, sourceDate));
        }
        unitsPerUsd = unitsPerUsd.setScale(DailyClose.RATE_SCALE, RoundingMode.HALF_EVEN);
    }

    /** USD needs no external rate. */
    public static ExchangeRate usd(LocalDate date) {
        return new ExchangeRate(Money.USD, date, BigDecimal.ONE, date, RateKind.CLOSE);
    }

    /**
     * Picks the rate for {@code date}: the closing rate of that day if there was trading,
     * otherwise the closing rate of the last trading day before it. Closes after {@code date}
     * and of other currencies are ignored.
     *
     * @return empty when there is no close on or before {@code date}
     */
    public static Optional<ExchangeRate> select(Currency currency, LocalDate date, Collection<DailyClose> closes) {
        return closes.stream()
                .filter(close -> close.currency().equals(currency))
                .filter(close -> !close.date().isAfter(date))
                .max(Comparator.comparing(DailyClose::date))
                .map(close -> new ExchangeRate(
                        currency,
                        date,
                        close.unitsPerUsd(),
                        close.date(),
                        close.date().equals(date) ? RateKind.CLOSE : RateKind.PREVIOUS_CLOSE));
    }

    /**
     * Converts an amount in this rate's currency to USD with a single rounding to cents
     * ({@link RoundingMode#HALF_EVEN}, banker's rounding).
     */
    public Money toUsd(Money money) {
        Objects.requireNonNull(money, "money");
        if (!money.isIn(currency)) {
            throw new IllegalArgumentException("Rate for %s cannot convert %s".formatted(currency, money));
        }
        if (money.isIn(Money.USD)) {
            return money;
        }
        return new Money(money.amount().divide(unitsPerUsd, Money.SCALE, RoundingMode.HALF_EVEN), Money.USD);
    }
}
