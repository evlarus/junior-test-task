package com.evlarus.spendinglimit.rate.infrastructure.twelvedata;

import com.evlarus.spendinglimit.rate.application.ExchangeRateProvider;
import com.evlarus.spendinglimit.rate.application.RateProviderException;
import com.evlarus.spendinglimit.rate.application.TransientRateProviderException;
import com.evlarus.spendinglimit.rate.domain.DailyClose;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Daily closes from Twelve Data for the pair {@code USD/<currency>}: the close is directly the number of
 * currency units per US dollar.
 *
 * <p>Failure handling: transient failures (HTTP 5xx, 429, refused connection, the same codes reported in the
 * body) are retried immediately with exponential backoff; everything else, including timeouts, fails at once.
 * Either way the caller keeps the transaction pending and retries it later, so no transaction is lost.
 */
@Component
@ConditionalOnProperty(name = "app.rates.provider", havingValue = "twelvedata")
class TwelveDataRateProvider implements ExchangeRateProvider {

    private static final String DAILY = "1day";

    private final TwelveDataApi api;

    TwelveDataRateProvider(TwelveDataApi api) {
        this.api = api;
    }

    @Override
    @Retryable(
            includes = TransientRateProviderException.class,
            maxRetriesString = "${app.rates.twelve-data.max-retries}",
            delayString = "${app.rates.twelve-data.retry-delay}",
            multiplier = 2)
    public List<DailyClose> dailyCloses(Currency currency, LocalDate from, LocalDate to) {
        // end_date is exclusive for daily candles; later closes are ignored by the caller anyway
        TimeSeriesResponse response = call(
                "USD/" + currency.getCurrencyCode(), from.toString(), to.plusDays(1).toString());
        if (!response.isOk()) {
            throw bodyError(response);
        }
        if (response.values() == null) {
            return List.of();
        }
        // Real responses sometimes list the same day twice with slightly different closes (seen for USD/RUB);
        // the first one listed is kept, so which rate a day gets never depends on chance
        Map<LocalDate, DailyClose> closesByDay = new LinkedHashMap<>();
        for (TimeSeriesResponse.Bar bar : response.values()) {
            DailyClose close = toClose(currency, bar);
            closesByDay.putIfAbsent(close.date(), close);
        }
        return List.copyOf(closesByDay.values());
    }

    private TimeSeriesResponse call(String symbol, String startDate, String endDate) {
        try {
            TimeSeriesResponse response = api.timeSeries(symbol, DAILY, startDate, endDate);
            if (response == null) {
                throw new RateProviderException("Twelve Data returned an empty body", null);
            }
            return response;
        } catch (HttpServerErrorException | HttpClientErrorException.TooManyRequests e) {
            throw new TransientRateProviderException("Twelve Data responded with " + e.getStatusCode(), e);
        } catch (RestClientResponseException e) {
            throw new RateProviderException("Twelve Data rejected the request with " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                throw new RateProviderException("Twelve Data did not respond in time", e);
            }
            throw new TransientRateProviderException("Twelve Data is unreachable: " + cause.getMessage(), e);
        } catch (RestClientException e) {
            throw new RateProviderException("Unreadable response from Twelve Data", e);
        }
    }

    private static RateProviderException bodyError(TimeSeriesResponse response) {
        int code = response.code() == null ? 0 : response.code();
        String message = "Twelve Data error %d: %s".formatted(code, response.message());
        return code == 429 || code >= 500
                ? new TransientRateProviderException(message, null)
                : new RateProviderException(message, null);
    }

    private static DailyClose toClose(Currency currency, TimeSeriesResponse.Bar bar) {
        try {
            // Daily candles are dated "2022-01-03"; intraday ones would carry a time after the date
            LocalDate date = LocalDate.parse(bar.datetime().substring(0, 10));
            return new DailyClose(currency, date, new BigDecimal(bar.close()));
        } catch (RuntimeException e) {
            throw new RateProviderException("Unreadable Twelve Data candle " + bar, e);
        }
    }
}
