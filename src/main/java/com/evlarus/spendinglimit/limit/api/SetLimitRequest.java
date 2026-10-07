package com.evlarus.spendinglimit.limit.api;

import com.evlarus.spendinglimit.common.api.ExpenseCategoryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** There is deliberately no date: a limit always takes effect at the moment the service receives it. */
@Schema(description = "New monthly limit in USD")
public record SetLimitRequest(
        @Schema(description = "Client account, 10 digits", example = "0000000123")
        @NotNull
        @Pattern(regexp = "\\d{10}", message = "must consist of exactly 10 digits")
        String account,

        @Schema(example = "product")
        @NotNull
        ExpenseCategoryDto expenseCategory,

        @Schema(description = "Limit amount in USD", example = "2000.00")
        @NotNull
        @PositiveOrZero
        @Digits(integer = 15, fraction = 2)
        BigDecimal limitSum,

        @Schema(description = "Optional, limits are always in USD", example = "USD", nullable = true)
        @Pattern(regexp = "USD", message = "must be USD")
        String limitCurrencyShortname) {
}
