package com.evlarus.spendinglimit.transaction.api;

import com.evlarus.spendinglimit.common.api.ExpenseCategoryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Transaction that exceeded the limit, with the limit it exceeded")
public record ExceededTransactionResponse(
        @Schema(example = "0000000123") String accountFrom,
        @Schema(example = "9999999999") String accountTo,
        @Schema(example = "KZT") String currencyShortname,
        @Schema(example = "270000.00") BigDecimal sum,
        @Schema(example = "product") ExpenseCategoryDto expenseCategory,
        @Schema(example = "2022-01-03T12:00:00+06:00") OffsetDateTime datetime,
        @Schema(example = "1000.00") BigDecimal limitSum,
        @Schema(example = "2022-01-01T00:00:00Z") OffsetDateTime limitDatetime,
        @Schema(example = "USD") String limitCurrencyShortname) {
}
