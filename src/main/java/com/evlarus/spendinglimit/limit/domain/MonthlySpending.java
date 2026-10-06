package com.evlarus.spendinglimit.limit.domain;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Running USD total of what one account has spent in one expense category during one business month.
 *
 * <p>Every transaction of that account, category and month is registered here, one at a time:
 * the stored row is locked while a transaction is registered, so two concurrent transactions
 * can never see the same remainder.
 */
public final class MonthlySpending {

    private final AccountNumber account;
    private final ExpenseCategory category;
    private final YearMonth month;
    private Money spent;

    public MonthlySpending(AccountNumber account, ExpenseCategory category, YearMonth month, Money spent) {
        this.account = Objects.requireNonNull(account, "account");
        this.category = Objects.requireNonNull(category, "category");
        this.month = Objects.requireNonNull(month, "month");
        Objects.requireNonNull(spent, "spent");
        if (!spent.isIn(Money.USD) || spent.isNegative()) {
            throw new IllegalArgumentException("Monthly spending must be a non-negative USD amount, got " + spent);
        }
        this.spent = spent;
    }

    /** Nothing spent yet in this month. */
    public static MonthlySpending startOf(AccountNumber account, ExpenseCategory category, YearMonth month) {
        return new MonthlySpending(account, category, month, Money.zero(Money.USD));
    }

    /**
     * Adds a transaction and checks it against the limit that was in force when the transaction was made.
     *
     * <p>The whole month counts, including transactions made before this limit was set: a new limit
     * of 2000 USD set after 1100 USD were spent leaves 900 USD.
     *
     * @param amountUsd the transaction amount converted to USD; zero is possible for tiny amounts
     * @param limit     the limit in force at the moment of the transaction
     */
    public LimitCheck register(Money amountUsd, SpendingLimit limit) {
        Objects.requireNonNull(amountUsd, "amountUsd");
        Objects.requireNonNull(limit, "limit");
        if (!amountUsd.isIn(Money.USD) || amountUsd.isNegative()) {
            throw new IllegalArgumentException("Transaction amount must be a non-negative USD amount, got " + amountUsd);
        }
        if (!limit.account().equals(account) || limit.category() != category) {
            throw new IllegalArgumentException("Limit belongs to another account or expense category");
        }
        spent = spent.add(amountUsd);
        return new LimitCheck(limit.amount().subtract(spent));
    }

    public AccountNumber account() {
        return account;
    }

    public ExpenseCategory category() {
        return category;
    }

    public YearMonth month() {
        return month;
    }

    public Money spent() {
        return spent;
    }
}
