package com.evlarus.spendinglimit.limit.application;

import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;

/**
 * A limit with statistics of the transactions that were checked against it.
 *
 * @param transactions         processed transactions checked against this limit
 * @param exceededTransactions those of them flagged {@code limit_exceeded}
 * @param spentUsd             their total in USD
 */
public record LimitUsage(SpendingLimit limit, long transactions, long exceededTransactions, Money spentUsd) {

    /** A limit no transaction has been checked against yet. */
    public static LimitUsage unused(SpendingLimit limit) {
        return new LimitUsage(limit, 0, 0, Money.zero(Money.USD));
    }
}
