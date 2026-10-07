package com.evlarus.spendinglimit.limit.api;

import com.evlarus.spendinglimit.common.api.ExpenseCategoryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Monthly limit with the transactions checked against it")
public record LimitResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "0000000123") String account,
        @Schema(example = "product") ExpenseCategoryDto expenseCategory,
        @Schema(example = "2000.00") BigDecimal limitSum,
        @Schema(description = "Moment the limit took effect", example = "2022-01-10T00:00:00Z") OffsetDateTime limitDatetime,
        @Schema(example = "USD") String limitCurrencyShortname,
        @Schema(description = "Set by the service (1000 USD) because the client had not set any")
        boolean systemDefault,
        @Schema(description = "Processed transactions checked against this limit", example = "4") long checkedTransactions,
        @Schema(description = "Of them, flagged limit_exceeded", example = "1") long exceededTransactions,
        @Schema(description = "Their total in USD", example = "1300.00") BigDecimal checkedSumUsd) {
}
