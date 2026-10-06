package com.evlarus.spendinglimit.limit.domain;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import java.time.Instant;
import java.util.Optional;

public interface SpendingLimitRepository {

    /** Stores a new limit and returns it with its id. Limits are never updated, so a saved limit is rejected. */
    SpendingLimit add(SpendingLimit limit);

    /**
     * The latest limit the client set at or before {@code moment}. The system default is never returned here:
     * being created lazily, it can be newer than a client limit and must not override it.
     */
    Optional<SpendingLimit> findLatestClientLimit(AccountNumber account, ExpenseCategory category, Instant moment);

    /**
     * The system default limit of the account and category, created from {@code candidate} when there is none.
     * Safe under concurrency: whatever number of callers race, exactly one default exists and all get it.
     */
    SpendingLimit findOrCreateSystemDefault(SpendingLimit candidate);
}
