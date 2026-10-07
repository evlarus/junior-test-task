package com.evlarus.spendinglimit.limit.domain;

import com.evlarus.spendinglimit.common.domain.ConflictException;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import java.util.Locale;

/** Two limits of one account and category cannot take effect at the same moment: it would be unclear which applies. */
public class LimitAlreadySetException extends ConflictException {

    public LimitAlreadySetException(ExpenseCategory category) {
        super("A %s limit has just been set for this account at the same moment; retry the request"
                .formatted(category.name().toLowerCase(Locale.ROOT)));
    }
}
