package com.evlarus.spendinglimit.transaction.application;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import java.time.OffsetDateTime;

public record ReceiveTransaction(
        AccountNumber accountFrom,
        AccountNumber accountTo,
        Money amount,
        ExpenseCategory category,
        OffsetDateTime occurredAt) {
}
