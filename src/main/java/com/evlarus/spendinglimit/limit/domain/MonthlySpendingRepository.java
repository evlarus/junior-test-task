package com.evlarus.spendinglimit.limit.domain;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import java.time.YearMonth;

public interface MonthlySpendingRepository {

    /**
     * Returns the spending of the account, category and month, locked until the current database transaction ends
     * (an empty one is created first if needed). A concurrent caller for the same key waits for that transaction,
     * then sees its total. Must be called inside a transaction.
     */
    MonthlySpending lockOrCreate(AccountNumber account, ExpenseCategory category, YearMonth month);

    /** Stores the new total. Only a spending locked by {@link #lockOrCreate} in the same transaction can be saved. */
    void save(MonthlySpending spending);
}
