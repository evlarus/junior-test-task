package com.evlarus.spendinglimit.transaction.api;

import com.evlarus.spendinglimit.common.api.CurrencyCode;
import com.evlarus.spendinglimit.common.api.ExpenseCategoryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Expense transaction reported by a bank system")
public record ReceiveTransactionRequest(
        @Schema(description = "Client account, 10 digits", example = "0000000123")
        @NotNull
        @Pattern(regexp = "\\d{10}", message = "must consist of exactly 10 digits")
        String accountFrom,

        @Schema(description = "Counterparty account, 10 digits", example = "9999999999")
        @NotNull
        @Pattern(regexp = "\\d{10}", message = "must consist of exactly 10 digits")
        String accountTo,

        @Schema(description = "ISO 4217 currency code", example = "KZT")
        @NotNull
        @CurrencyCode
        String currencyShortname,

        @Schema(example = "10000.45")
        @NotNull
        @Positive
        @Digits(integer = 15, fraction = 2)
        BigDecimal sum,

        @Schema(example = "product")
        @NotNull
        ExpenseCategoryDto expenseCategory,

        @Schema(description = "Moment of the transaction with its time-zone offset", example = "2022-01-30T00:00:00+06:00")
        @NotNull
        OffsetDateTime datetime) {
}
