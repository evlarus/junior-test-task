package com.evlarus.spendinglimit.rate.infrastructure.twelvedata;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Response of {@code /time_series}. Prices come as strings ({@code "close": "433.10000"}); errors come with
 * {@code "status": "error"}, a {@code code} and a {@code message}, often with HTTP 200. Only the needed fields are
 * mapped; the rest is ignored by the lenient mapper of this client.
 */
record TimeSeriesResponse(
        @Nullable String status,
        @Nullable Integer code,
        @Nullable String message,
        @Nullable List<Bar> values) {

    boolean isOk() {
        return "ok".equals(status);
    }

    record Bar(@Nullable String datetime, @Nullable String close) {
    }
}
