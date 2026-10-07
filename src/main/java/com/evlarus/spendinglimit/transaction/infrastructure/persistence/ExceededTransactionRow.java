package com.evlarus.spendinglimit.transaction.infrastructure.persistence;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.transaction.application.ExceededTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;

public record ExceededTransactionRow(
        String accountFrom,
        String accountTo,
        String currency,
        BigDecimal amount,
        ExpenseCategory expenseCategory,
        Instant occurredAt,
        int occurredOffsetSeconds,
        BigDecimal limitSum,
        String limitCurrency,
        Instant limitDatetime) {

    ExceededTransaction toView() {
        return new ExceededTransaction(
                AccountNumber.of(accountFrom),
                AccountNumber.of(accountTo),
                new Money(amount, Currency.getInstance(currency)),
                expenseCategory,
                occurredAt.atOffset(ZoneOffset.ofTotalSeconds(occurredOffsetSeconds)),
                new Money(limitSum, Currency.getInstance(limitCurrency)),
                limitDatetime);
    }
}
