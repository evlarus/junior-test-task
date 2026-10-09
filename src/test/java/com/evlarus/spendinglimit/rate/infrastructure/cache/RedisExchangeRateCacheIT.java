package com.evlarus.spendinglimit.rate.infrastructure.cache;

import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.stubCloses;
import static com.evlarus.spendinglimit.support.TwelveDataStubs.timeSeriesRequested;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static org.assertj.core.api.Assertions.assertThat;

import com.evlarus.spendinglimit.rate.application.ExchangeRateService;
import com.evlarus.spendinglimit.rate.application.RateCacheProperties;
import com.evlarus.spendinglimit.rate.application.RateLookup;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.rate.domain.RateKind;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class RedisExchangeRateCacheIT {

    private static final AtomicInteger NEXT_WEEKS = new AtomicInteger();

    @Autowired
    private RedisExchangeRateCache cache;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ExchangeRateService service;

    @Autowired
    private WireMockServer twelveDataMock;

    @Autowired
    private JdbcClient jdbc;

    private final LocalDate monday = LocalDate.of(2010, 1, 4).plusWeeks(NEXT_WEEKS.getAndAdd(3));

    @BeforeEach
    void resetMock() {
        twelveDataMock.resetAll();
    }

    @Test
    void storesRatesWithAnExpiry() {
        ExchangeRate rate = new ExchangeRate(KZT, monday, new BigDecimal("433.1"), monday, RateKind.CLOSE);

        cache.put(rate);

        assertThat(cache.get(KZT, monday)).contains(rate);
        assertThat(redis.getExpire(RedisExchangeRateCache.key(KZT, monday))).isPositive();
    }

    @Test
    void rateFetchedOnceIsServedFromRedisEvenWithoutTheDatabaseRow() {
        stubCloses(twelveDataMock, "KZT", Map.of(monday, "433.10"));
        RateLookup first = service.findRate(KZT, monday);
        jdbc.sql("delete from exchange_rate where currency = 'KZT' and rate_date = ?").param(monday).update();

        RateLookup second = service.findRate(KZT, monday);

        assertThat(second).isEqualTo(first);
        twelveDataMock.verify(exactly(1), timeSeriesRequested("KZT"));
    }

    @Test
    void unavailableRedisIsTreatedAsAMiss() {
        LettuceConnectionFactory deadRedis = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).build());
        deadRedis.afterPropertiesSet();
        deadRedis.start();
        try {
            RedisExchangeRateCache unreachable = new RedisExchangeRateCache(
                    new StringRedisTemplate(deadRedis), new RateCacheProperties(RateCacheProperties.Type.REDIS, Duration.ofDays(1)));

            unreachable.put(new ExchangeRate(KZT, monday, BigDecimal.TEN, monday, RateKind.CLOSE));

            assertThat(unreachable.get(KZT, monday)).isEmpty();
        } finally {
            deadRedis.destroy();
        }
    }
}
