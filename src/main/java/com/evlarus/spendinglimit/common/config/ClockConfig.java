package com.evlarus.spendinglimit.common.config;

import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single source of the current time. Code never calls {@code Instant.now()} directly,
 * so tests can replace this bean with a controllable clock and replay scenarios on any date.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BusinessCalendar businessCalendar(AppProperties properties) {
        return new BusinessCalendar(properties.businessZone());
    }
}
