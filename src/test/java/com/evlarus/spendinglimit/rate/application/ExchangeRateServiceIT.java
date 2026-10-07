package com.evlarus.spendinglimit.rate.application;

import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.stubCloses;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.timeSeries;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.timeSeriesRequest;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.timeSeriesRequested;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.rate.domain.ExchangeRateRepository;
import com.evlarus.spendinglimit.rate.domain.RateKind;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ExchangeRateServiceIT {

    /** Each test works with its own weeks, far enough apart that the 10-day lookback windows never overlap. */
    private static final AtomicInteger NEXT_WEEKS = new AtomicInteger();

    @Autowired
    private ExchangeRateService service;

    @Autowired
    private ExchangeRateRepository repository;

    @Autowired
    private WireMockServer twelveDataMock;

    private final LocalDate monday = LocalDate.of(2015, 1, 5).plusWeeks(NEXT_WEEKS.getAndAdd(3));
    private final LocalDate friday = monday.minusDays(3);
    private final LocalDate sunday = monday.minusDays(1);

    @BeforeEach
    void resetMock() {
        twelveDataMock.resetAll();
    }

    @Test
    void paysForADayOnlyOnceAndReusesTheFetchedNeighbours() {
        stubCloses(twelveDataMock, "KZT", Map.of(friday, "431.8", monday, "433.1"));

        RateLookup first = service.findRate(KZT, monday);
        RateLookup again = service.findRate(KZT, monday);
        RateLookup neighbour = service.findRate(KZT, friday);

        assertThat(first).isEqualTo(found(monday, "433.1", monday, RateKind.CLOSE));
        assertThat(again).isEqualTo(first);
        assertThat(neighbour).isEqualTo(found(friday, "431.8", friday, RateKind.CLOSE));
        twelveDataMock.verify(exactly(1), timeSeriesRequested("KZT"));
    }

    @Test
    void weekendUsesThePreviousCloseAndRemembersIt() {
        stubCloses(twelveDataMock, "KZT", Map.of(friday, "431.8"));

        RateLookup lookup = service.findRate(KZT, sunday);

        assertThat(lookup).isEqualTo(found(sunday, "431.8", friday, RateKind.PREVIOUS_CLOSE));
        assertThat(repository.find(KZT, sunday)).isPresent();
        assertThat(service.findRate(KZT, sunday)).isEqualTo(lookup);
        twelveDataMock.verify(exactly(1), timeSeriesRequested("KZT"));
    }

    @Test
    void providerFailureMakesTheRateUnavailableAndStoresNothing() {
        twelveDataMock.stubFor(timeSeriesRequest("KZT").willReturn(status(503)));

        assertThat(service.findRate(KZT, monday)).isInstanceOf(RateLookup.Unavailable.class);
        assertThat(repository.find(KZT, monday)).isEmpty();
    }

    @Test
    void usdNeedsNoProvider() {
        assertThat(service.findRate(Money.USD, monday)).isEqualTo(new RateLookup.Found(ExchangeRate.usd(monday)));
        twelveDataMock.verify(exactly(0), timeSeriesRequested("USD"));
    }

    @Test
    void concurrentMissesOfTheSameDayShareOneRequest() throws Exception {
        // The delay keeps the request in flight while the other callers arrive; it stays well below the
        // 500 ms test read-timeout, which for the JDK client covers the whole exchange including connecting
        twelveDataMock.stubFor(timeSeriesRequest("KZT")
                .willReturn(okJson(timeSeries("KZT", Map.of(monday, "433.1"))).withFixedDelay(100)));
        List<Callable<RateLookup>> callers = IntStream.range(0, 8)
                .<Callable<RateLookup>>mapToObj(i -> () -> service.findRate(KZT, monday))
                .toList();

        List<RateLookup> lookups;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            lookups = executor.invokeAll(callers).stream().map(ExchangeRateServiceIT::get).toList();
        }

        assertThat(lookups).containsOnly(found(monday, "433.1", monday, RateKind.CLOSE));
        twelveDataMock.verify(exactly(1), timeSeriesRequested("KZT"));
    }

    private static RateLookup found(LocalDate day, String unitsPerUsd, LocalDate source, RateKind kind) {
        return new RateLookup.Found(new ExchangeRate(KZT, day, new BigDecimal(unitsPerUsd), source, kind));
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
