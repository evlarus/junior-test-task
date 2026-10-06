package com.evlarus.spendinglimit.support;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;

/** Shared values for domain unit tests: accounts, currencies, calendar and ready-made limits. */
public final class DomainFixtures {

    public static final Currency KZT = Currency.getInstance("KZT");
    public static final Currency RUB = Currency.getInstance("RUB");

    public static final AccountNumber CLIENT = AccountNumber.of("0000000123");
    public static final AccountNumber OTHER_CLIENT = AccountNumber.of("0000000456");
    public static final AccountNumber COUNTERPARTY = AccountNumber.of("9999999999");

    public static final BusinessCalendar UTC_CALENDAR = new BusinessCalendar(ZoneOffset.UTC);

    private DomainFixtures() {
    }

    public static Instant instant(String isoInstant) {
        return Instant.parse(isoInstant);
    }

    public static OffsetDateTime dateTime(String isoDateTime) {
        return OffsetDateTime.parse(isoDateTime);
    }

    /** A saved limit set by {@link #CLIENT} for products. */
    public static SpendingLimit clientLimit(long id, String usd, String setAt) {
        return new SpendingLimit(id, CLIENT, ExpenseCategory.PRODUCT, Money.usd(usd), instant(setAt), false);
    }

    /** A saved system default limit of 1000 USD of {@link #CLIENT} for products. */
    public static SpendingLimit defaultLimit(long id, String setAt) {
        return new SpendingLimit(id, CLIENT, ExpenseCategory.PRODUCT, Money.usd("1000"), instant(setAt), true);
    }
}
