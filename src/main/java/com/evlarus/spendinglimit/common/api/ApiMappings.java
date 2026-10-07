package com.evlarus.spendinglimit.common.api;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Currency;
import org.springframework.stereotype.Component;

/** Conversions of domain value types to their JSON form, used by the MapStruct mappers of all APIs. */
@Component
public class ApiMappings {

    private final BusinessCalendar calendar;

    public ApiMappings(BusinessCalendar calendar) {
        this.calendar = calendar;
    }

    public String account(AccountNumber account) {
        return account.value();
    }

    public String currencyCode(Currency currency) {
        return currency.getCurrencyCode();
    }

    /** Moments the service itself sets (limit dates) are shown in the business time zone. */
    public OffsetDateTime businessDateTime(Instant instant) {
        return instant.atZone(calendar.zone()).toOffsetDateTime();
    }
}
