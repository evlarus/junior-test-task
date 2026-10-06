package com.evlarus.spendinglimit.transaction.domain;

import com.evlarus.spendinglimit.common.domain.DomainException;

/** A transaction is counted against the limit exactly once; a second attempt is a bug or a race and is refused. */
public class TransactionAlreadyProcessedException extends DomainException {

    public TransactionAlreadyProcessedException(Long transactionId) {
        super("Transaction %s has already been processed".formatted(transactionId));
    }
}
