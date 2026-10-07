package com.evlarus.spendinglimit.rate.infrastructure.twelvedata;

import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.error;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.timeSeries;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.timeSeriesRequest;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.timeSeriesRequested;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evlarus.spendinglimit.rate.application.ExchangeRateProvider;
import com.evlarus.spendinglimit.rate.application.RateProviderException;
import com.evlarus.spendinglimit.rate.application.TransientRateProviderException;
import com.evlarus.spendinglimit.rate.domain.DailyClose;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class TwelveDataRateProviderIT {

    private static final LocalDate FRIDAY = LocalDate.of(2021, 12, 31);
    private static final LocalDate MONDAY = LocalDate.of(2022, 1, 3);
    private static final String CLOSES = timeSeries("KZT", Map.of(FRIDAY, "431.80000", MONDAY, "433.10000"));

    @Autowired
    private ExchangeRateProvider provider;

    @Autowired
    private WireMockServer twelveDataMock;

    @BeforeEach
    void resetMock() {
        twelveDataMock.resetAll();
    }

    @Test
    void readsDailyClosesAndSendsTheKeyInAHeader() {
        twelveDataMock.stubFor(timeSeriesRequest("KZT").willReturn(okJson(CLOSES)));

        List<DailyClose> closes = provider.dailyCloses(KZT, FRIDAY, MONDAY);

        assertThat(closes).containsExactlyInAnyOrder(
                new DailyClose(KZT, FRIDAY, new BigDecimal("431.8")),
                new DailyClose(KZT, MONDAY, new BigDecimal("433.1")));
        twelveDataMock.verify(exactly(1), timeSeriesRequested("KZT")
                .withQueryParam("interval", equalTo("1day"))
                .withQueryParam("start_date", equalTo("2021-12-31"))
                .withQueryParam("end_date", equalTo("2022-01-04"))
                .withQueryParam("apikey", absent())
                .withHeader("Authorization", equalTo("apikey test-api-key")));
    }

    @Test
    void dayListedTwiceKeepsTheFirstClose() {
        // Shape of a real USD/RUB response from October 2026
        twelveDataMock.stubFor(timeSeriesRequest("KZT").willReturn(okJson("""
                {"values":[{"datetime":"2022-01-03","close":"433.10000"},
                           {"datetime":"2022-01-03","close":"433.15000"},
                           {"datetime":"2021-12-31","close":"431.80000"}],
                 "status":"ok"}""")));

        assertThat(provider.dailyCloses(KZT, FRIDAY, MONDAY)).containsExactly(
                new DailyClose(KZT, MONDAY, new BigDecimal("433.1")),
                new DailyClose(KZT, FRIDAY, new BigDecimal("431.8")));
    }

    @Test
    void errorReportedInTheBodyWithHttp200IsAFailure() {
        twelveDataMock.stubFor(timeSeriesRequest("KZT").willReturn(okJson(error(400, "**symbol** not found"))));

        assertThatThrownBy(() -> provider.dailyCloses(KZT, FRIDAY, MONDAY))
                .isInstanceOf(RateProviderException.class)
                .isNotInstanceOf(TransientRateProviderException.class)
                .hasMessageContaining("400");
        twelveDataMock.verify(exactly(1), timeSeriesRequested("KZT"));
    }

    @Test
    void serverErrorsAreRetriedUntilTheProviderRecovers() {
        respondInSequence(serverError(), serverError(), okJson(CLOSES));

        assertThat(provider.dailyCloses(KZT, FRIDAY, MONDAY)).hasSize(2);
        twelveDataMock.verify(exactly(3), timeSeriesRequested("KZT"));
    }

    @Test
    void persistentServerErrorFailsAfterTheRetries() {
        twelveDataMock.stubFor(timeSeriesRequest("KZT").willReturn(status(503)));

        assertThatThrownBy(() -> provider.dailyCloses(KZT, FRIDAY, MONDAY))
                .isInstanceOf(TransientRateProviderException.class);
        twelveDataMock.verify(exactly(3), timeSeriesRequested("KZT"));
    }

    @Test
    void rateLimitingIsRetriedWhetherReportedByStatusOrInTheBody() {
        respondInSequence(status(429), okJson(error(429, "You have run out of API credits")), okJson(CLOSES));

        assertThat(provider.dailyCloses(KZT, FRIDAY, MONDAY)).hasSize(2);
        twelveDataMock.verify(exactly(3), timeSeriesRequested("KZT"));
    }

    @Test
    void timeoutIsNotRetried() {
        // read-timeout is 500 ms in the test profile
        twelveDataMock.stubFor(timeSeriesRequest("KZT").willReturn(okJson(CLOSES).withFixedDelay(1_500)));
        long started = System.nanoTime();

        assertThatThrownBy(() -> provider.dailyCloses(KZT, FRIDAY, MONDAY))
                .isInstanceOf(RateProviderException.class)
                .isNotInstanceOf(TransientRateProviderException.class)
                .hasMessageContaining("did not respond in time");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1_400));
        twelveDataMock.verify(exactly(1), timeSeriesRequested("KZT"));
    }

    @Test
    void rejectedRequestIsNotRetried() {
        twelveDataMock.stubFor(timeSeriesRequest("KZT").willReturn(status(401)));

        assertThatThrownBy(() -> provider.dailyCloses(KZT, FRIDAY, MONDAY))
                .isInstanceOf(RateProviderException.class)
                .isNotInstanceOf(TransientRateProviderException.class);
        twelveDataMock.verify(exactly(1), timeSeriesRequested("KZT"));
    }

    @Test
    void unreadableResponseIsAFailure() {
        twelveDataMock.stubFor(timeSeriesRequest("KZT").willReturn(okJson("{\"values\": [")));

        assertThatThrownBy(() -> provider.dailyCloses(KZT, FRIDAY, MONDAY)).isInstanceOf(RateProviderException.class);
    }

    @Test
    void unreadableCandleIsAFailure() {
        twelveDataMock.stubFor(timeSeriesRequest("KZT").willReturn(okJson(timeSeries("KZT", Map.of(MONDAY, "n/a")))));

        assertThatThrownBy(() -> provider.dailyCloses(KZT, FRIDAY, MONDAY))
                .isInstanceOf(RateProviderException.class)
                .hasMessageContaining("candle");
    }

    private void respondInSequence(ResponseDefinitionBuilder... responses) {
        for (int i = 0; i < responses.length; i++) {
            twelveDataMock.stubFor(timeSeriesRequest("KZT")
                    .inScenario("sequence")
                    .whenScenarioStateIs(i == 0 ? STARTED : "step " + i)
                    .willSetStateTo("step " + (i + 1))
                    .willReturn(responses[i]));
        }
    }
}
