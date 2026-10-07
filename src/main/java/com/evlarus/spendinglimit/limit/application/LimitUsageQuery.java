package com.evlarus.spendinglimit.limit.application;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import java.util.List;

/** Read model for the client API: limits of an account with their usage, in a single query. */
public interface LimitUsageQuery {

    /** All limits of the account, by category and newest first, including the system default once it exists. */
    List<LimitUsage> findByAccount(AccountNumber account);
}
