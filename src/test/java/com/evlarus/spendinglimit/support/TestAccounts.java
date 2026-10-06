package com.evlarus.spendinglimit.support;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Integration tests share one database for the whole run; each test isolates its data
 * by using account numbers no other test uses, instead of cleaning tables.
 */
public final class TestAccounts {

    private static final AtomicLong NEXT = new AtomicLong(1_000_000_000L);

    private TestAccounts() {
    }

    public static AccountNumber unique() {
        return AccountNumber.of(Long.toString(NEXT.getAndIncrement()));
    }
}
