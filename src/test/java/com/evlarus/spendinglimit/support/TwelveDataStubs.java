package com.evlarus.spendinglimit.support;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/** Builders of Twelve Data responses and request matchers for WireMock. */
public final class TwelveDataStubs {

    public static final String TIME_SERIES = "/time_series";

    private TwelveDataStubs() {
    }

    /** Daily time series of {@code USD/<currency>}; contains extra fields like the real API does. */
    public static String timeSeries(String currency, Map<LocalDate, String> closesByDay) {
        String values = closesByDay.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, String>comparingByKey(Comparator.reverseOrder()))
                .map(close -> """
                        {"datetime":"%s","open":"%s","high":"%s","low":"%s","close":"%s"}"""
                        .formatted(close.getKey(), close.getValue(), close.getValue(), close.getValue(), close.getValue()))
                .collect(Collectors.joining(","));
        return """
                {"meta":{"symbol":"USD/%s","interval":"1day","currency_base":"US Dollar","type":"Physical Currency"},
                 "values":[%s],"status":"ok"}""".formatted(currency, values);
    }

    /** Error as Twelve Data reports it: in the body, with HTTP 200. */
    public static String error(int code, String message) {
        return """
                {"code":%d,"message":"%s","status":"error"}""".formatted(code, message);
    }

    public static MappingBuilder timeSeriesRequest(String currency) {
        return get(urlPathEqualTo(TIME_SERIES)).withQueryParam("symbol", equalTo("USD/" + currency));
    }

    public static RequestPatternBuilder timeSeriesRequested(String currency) {
        return getRequestedFor(urlPathEqualTo(TIME_SERIES)).withQueryParam("symbol", equalTo("USD/" + currency));
    }

    public static void stubCloses(WireMockServer server, String currency, Map<LocalDate, String> closesByDay) {
        server.stubFor(timeSeriesRequest(currency).willReturn(okJson(timeSeries(currency, closesByDay))));
    }
}
