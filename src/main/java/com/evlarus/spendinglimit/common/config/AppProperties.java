package com.evlarus.spendinglimit.common.config;

import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param businessZone time zone in which calendar days and months are counted (month boundaries of limits,
 *                     the day of an exchange rate); see {@code BusinessCalendar}
 */
@ConfigurationProperties("app")
@Validated
public record AppProperties(@NotNull ZoneId businessZone) {
}
