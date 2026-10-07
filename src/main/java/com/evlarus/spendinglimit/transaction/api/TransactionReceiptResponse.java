package com.evlarus.spendinglimit.transaction.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Result of receiving a transaction")
public record TransactionReceiptResponse(
        @Schema(example = "1001") long id,
        @Schema(description = "processed, or pending while the exchange rate is unavailable", example = "processed")
        String status,
        @Schema(description = "Amount in USD; absent while pending", example = "22.22", nullable = true)
        BigDecimal sumUsd,
        @Schema(description = "Absent while pending", example = "false", nullable = true)
        Boolean limitExceeded) {
}
