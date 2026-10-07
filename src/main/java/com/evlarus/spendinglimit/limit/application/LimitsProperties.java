package com.evlarus.spendinglimit.limit.application;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.limits")
@Validated
public record LimitsProperties(@NotNull @PositiveOrZero @Digits(integer = 15, fraction = 2) BigDecimal defaultAmount) {
}
