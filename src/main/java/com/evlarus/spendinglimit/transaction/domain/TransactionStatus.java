package com.evlarus.spendinglimit.transaction.domain;

public enum TransactionStatus {

    /** Saved, but not converted to USD and not checked against the limit yet (for example, no exchange rate). */
    PENDING,

    /** Converted to USD and checked against the limit; {@code limit_exceeded} is final. */
    PROCESSED
}
