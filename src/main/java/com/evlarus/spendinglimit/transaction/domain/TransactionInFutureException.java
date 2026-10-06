package com.evlarus.spendinglimit.transaction.domain;

import com.evlarus.spendinglimit.common.domain.DomainException;
import java.time.OffsetDateTime;

/**
 * Transactions arrive in real time, so one dated in the future is invalid input. Accepting it would also
 * leave it pending forever: there is no exchange rate for a day that has not come yet.
 */
public class TransactionInFutureException extends DomainException {

    public TransactionInFutureException(OffsetDateTime occurredAt) {
        super("Transaction date %s is in the future".formatted(occurredAt));
    }
}
