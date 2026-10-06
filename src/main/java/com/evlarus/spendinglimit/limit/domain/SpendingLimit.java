package com.evlarus.spendinglimit.limit.domain;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.common.domain.Timestamps;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Monthly spending limit of one account in one expense category, in USD. Immutable: a limit is never
 * updated, a client sets a new one instead, and the new one only affects transactions made after it.
 *
 * @param id            database identity, {@code null} until the limit is saved
 * @param setAt         moment the limit was set; always "now" of the service, never chosen by the client
 * @param systemDefault {@code true} for the limit the service creates itself when the client has not set any
 */
public record SpendingLimit(
        @Nullable Long id,
        AccountNumber account,
        ExpenseCategory category,
        Money amount,
        Instant setAt,
        boolean systemDefault) {

    public SpendingLimit {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(setAt, "setAt");
        if (!amount.isIn(Money.USD)) {
            throw new IllegalArgumentException("Spending limit must be set in USD, got " + amount.currency());
        }
        if (amount.isNegative()) {
            throw new IllegalArgumentException("Spending limit must not be negative");
        }
        setAt = Timestamps.normalize(setAt);
    }

    /** A limit the client sets; it takes effect at {@code now}. */
    public static SpendingLimit setByClient(AccountNumber account, ExpenseCategory category, Money amount, Instant now) {
        return new SpendingLimit(null, account, category, amount, now, false);
    }

    /** The limit applied while the client has not set one (1000 USD by configuration). */
    public static SpendingLimit systemDefault(
            AccountNumber account, ExpenseCategory category, Money amount, Instant now) {
        return new SpendingLimit(null, account, category, amount, now, true);
    }

    public boolean isPersisted() {
        return id != null;
    }
}
