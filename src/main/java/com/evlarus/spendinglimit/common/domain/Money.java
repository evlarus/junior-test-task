package com.evlarus.spendinglimit.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Amount of money in an ISO 4217 currency, always with exactly two decimal places.
 *
 * <p>The scale is normalized on creation, so {@code 10.5} and {@code 10.50} are equal and compare
 * the same way after a database round trip. An amount that would need rounding is rejected:
 * money is never rounded silently.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final int SCALE = 2;
    public static final Currency USD = Currency.getInstance("USD");

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        try {
            amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Amount must have at most %d decimal places: %s".formatted(SCALE, amount.toPlainString()), e);
        }
    }

    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money usd(String amount) {
        return of(amount, USD);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isIn(Currency expected) {
        return currency.equals(expected);
    }

    private void requireSameCurrency(Money other) {
        if (!isIn(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: %s and %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
