package com.evlarus.spendinglimit.limit.application;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.LimitAlreadySetException;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.limit.domain.SpendingLimitRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Use cases of the client API around limits. */
@Service
public class LimitService {

    private static final Logger log = LoggerFactory.getLogger(LimitService.class);

    private final SpendingLimitRepository repository;
    private final LimitUsageQuery usageQuery;
    private final Clock clock;

    public LimitService(SpendingLimitRepository repository, LimitUsageQuery usageQuery, Clock clock) {
        this.repository = repository;
        this.usageQuery = usageQuery;
        this.clock = clock;
    }

    /**
     * Sets a new limit that takes effect now. The moment is always the service's current time: a client can
     * neither backdate a limit nor schedule one, and existing limits are never changed.
     *
     * @throws LimitAlreadySetException when another limit of the account and category took effect at the same moment
     */
    public SpendingLimit setLimit(AccountNumber account, ExpenseCategory category, Money amount) {
        SpendingLimit saved = repository.add(SpendingLimit.setByClient(account, category, amount, clock.instant()));
        log.info("Limit {} of {} USD set for account {}, {}", saved.id(), amount.amount(), account, category);
        return saved;
    }

    public List<LimitUsage> getLimits(AccountNumber account) {
        return usageQuery.findByAccount(account);
    }
}
