package com.evlarus.spendinglimit.rate.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Exchange rate settings ({@code app.rates.*}). Connection settings of Twelve Data (base URL, timeouts,
 * the API key header) are in {@code spring.http.serviceclient.twelvedata.*}.
 *
 * @param lookbackDays        how many days before the requested one are fetched in one request; covers weekends
 *                            and holidays and fills the database for neighbouring days at no extra cost
 * @param supportedCurrencies currencies accepted in transactions (besides USD)
 */
@ConfigurationProperties("app.rates")
@Validated
public record RatesProperties(
        @NotNull Provider provider,
        @Min(1) @Max(31) int lookbackDays,
        @NotEmpty Set<Currency> supportedCurrencies,
        @NotNull @Valid TwelveData twelveData,
        @NotNull @Valid Fixed fixed) {

    public enum Provider {
        TWELVEDATA,
        FIXED
    }

    public record TwelveData(String apiKey) {
    }

    /** Rates used by the {@code fixed} provider: local runs and demos without an API key. */
    public record Fixed(Map<Currency, BigDecimal> unitsPerUsd) {
    }

    @AssertTrue(message = "TWELVEDATA_API_KEY must be set when the rate provider is twelvedata;"
            + " set RATES_PROVIDER=fixed to run without it")
    public boolean isApiKeyPresentWhenRequired() {
        return provider != Provider.TWELVEDATA
                || twelveData != null && twelveData.apiKey() != null && !twelveData.apiKey().isBlank();
    }
}
