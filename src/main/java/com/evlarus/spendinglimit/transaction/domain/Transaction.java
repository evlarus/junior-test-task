package com.evlarus.spendinglimit.transaction.domain;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.common.domain.Timestamps;
import com.evlarus.spendinglimit.limit.domain.LimitCheck;
import com.evlarus.spendinglimit.limit.domain.MonthlySpending;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An expense transaction of a client account.
 *
 * <p>Lifecycle: {@link #receive received} as {@link TransactionStatus#PENDING}, then {@link #process processed}
 * exactly once. The status is derived from the presence of a {@link ProcessingResult}, so a "processed
 * transaction without a flag" cannot exist.
 */
public final class Transaction {

    private final @Nullable Long id;
    private final AccountNumber accountFrom;
    private final AccountNumber accountTo;
    private final Money amount;
    private final ExpenseCategory category;
    private final OffsetDateTime occurredAt;
    private final Instant receivedAt;
    private @Nullable ProcessingResult result;

    /** Restores a transaction from storage. New transactions are created with {@link #receive}. */
    public Transaction(
            @Nullable Long id,
            AccountNumber accountFrom,
            AccountNumber accountTo,
            Money amount,
            ExpenseCategory category,
            OffsetDateTime occurredAt,
            Instant receivedAt,
            @Nullable ProcessingResult result) {
        this.id = id;
        this.accountFrom = Objects.requireNonNull(accountFrom, "accountFrom");
        this.accountTo = Objects.requireNonNull(accountTo, "accountTo");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.category = Objects.requireNonNull(category, "category");
        this.occurredAt = Timestamps.normalize(Objects.requireNonNull(occurredAt, "occurredAt"));
        this.receivedAt = Timestamps.normalize(Objects.requireNonNull(receivedAt, "receivedAt"));
        this.result = result;
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Transaction amount must be positive, got " + amount);
        }
    }

    /**
     * Accepts a new transaction.
     *
     * @param allowedClockSkew how far in the future {@code occurredAt} may be, to tolerate clock differences
     *                         between the bank systems and this service
     * @throws TransactionInFutureException when the transaction is dated later than {@code now + allowedClockSkew}
     */
    public static Transaction receive(
            AccountNumber accountFrom,
            AccountNumber accountTo,
            Money amount,
            ExpenseCategory category,
            OffsetDateTime occurredAt,
            Instant now,
            Duration allowedClockSkew) {
        if (occurredAt.toInstant().isAfter(now.plus(allowedClockSkew))) {
            throw new TransactionInFutureException(occurredAt);
        }
        return new Transaction(null, accountFrom, accountTo, amount, category, occurredAt, now, null);
    }

    /**
     * Converts the amount to USD, adds it to the month's spending and fixes the {@code limit_exceeded} flag.
     *
     * @param rate     rate for the business day of this transaction
     * @param spending spending of this account and category in the business month of this transaction
     * @param limit    the limit in force at the moment of this transaction (saved, so it can be referenced)
     * @return the check result, including the remainder of the limit after this transaction
     * @throws TransactionAlreadyProcessedException when called for an already processed transaction
     */
    public LimitCheck process(
            ExchangeRate rate, MonthlySpending spending, SpendingLimit limit, BusinessCalendar calendar, Instant now) {
        if (result != null) {
            throw new TransactionAlreadyProcessedException(id);
        }
        if (!rate.rateDate().equals(businessDate(calendar))) {
            throw new IllegalArgumentException(
                    "Rate for %s cannot be used for a transaction of %s".formatted(rate.rateDate(), businessDate(calendar)));
        }
        if (!spending.account().equals(accountFrom) || spending.category() != category
                || !spending.month().equals(businessMonth(calendar))) {
            throw new IllegalArgumentException("Monthly spending belongs to another account, category or month");
        }
        Long limitId = limit.id();
        if (limitId == null) {
            throw new IllegalArgumentException("Limit must be saved before it is applied");
        }
        if (!limit.systemDefault() && limit.setAt().isAfter(occurredAt.toInstant())) {
            throw new IllegalArgumentException("A limit set after the transaction cannot apply to it");
        }

        Money amountUsd = rate.toUsd(amount);
        LimitCheck check = spending.register(amountUsd, limit);
        result = new ProcessingResult(amountUsd, limitId, check.exceeded(), now);
        return check;
    }

    public TransactionStatus status() {
        return result == null ? TransactionStatus.PENDING : TransactionStatus.PROCESSED;
    }

    public boolean isPending() {
        return result == null;
    }

    /** Day of the transaction in the business time zone; the exchange rate of this day is used. */
    public LocalDate businessDate(BusinessCalendar calendar) {
        return calendar.dateOf(occurredAt.toInstant());
    }

    /** Month of the transaction in the business time zone; the transaction counts against this month's limit. */
    public YearMonth businessMonth(BusinessCalendar calendar) {
        return calendar.monthOf(occurredAt.toInstant());
    }

    public @Nullable Long id() {
        return id;
    }

    public AccountNumber accountFrom() {
        return accountFrom;
    }

    public AccountNumber accountTo() {
        return accountTo;
    }

    public Money amount() {
        return amount;
    }

    public ExpenseCategory category() {
        return category;
    }

    /** As sent by the client, including its time-zone offset. */
    public OffsetDateTime occurredAt() {
        return occurredAt;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public Optional<ProcessingResult> result() {
        return Optional.ofNullable(result);
    }
}
