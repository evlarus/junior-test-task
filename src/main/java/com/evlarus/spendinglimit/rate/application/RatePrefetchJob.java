package com.evlarus.spendinglimit.rate.application;

import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fetches the day's rates early, so the first transactions of the day do not wait for the provider. */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class RatePrefetchJob {

    private final ExchangeRateService service;
    private final BusinessCalendar calendar;
    private final Clock clock;

    RatePrefetchJob(ExchangeRateService service, BusinessCalendar calendar, Clock clock) {
        this.service = service;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.rates.prefetch-cron}", zone = "${app.business-zone}")
    void prefetchToday() {
        service.prefetch(calendar.dateOf(clock.instant()));
    }
}
