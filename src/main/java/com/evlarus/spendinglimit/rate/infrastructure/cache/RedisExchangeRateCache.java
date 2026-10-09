package com.evlarus.spendinglimit.rate.infrastructure.cache;

import com.evlarus.spendinglimit.rate.application.ExchangeRateCache;
import com.evlarus.spendinglimit.rate.application.RateCacheProperties;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.rate.domain.RateKind;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "app.rates.cache.type", havingValue = "redis")
class RedisExchangeRateCache implements ExchangeRateCache {

    static final String KEY_PREFIX = "exchange-rate:";

    private static final Logger log = LoggerFactory.getLogger(RedisExchangeRateCache.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final JsonMapper mapper = JsonMapper.builder().build();

    RedisExchangeRateCache(StringRedisTemplate redis, RateCacheProperties properties) {
        this.redis = redis;
        this.ttl = properties.ttl();
    }

    @Override
    public Optional<ExchangeRate> get(Currency currency, LocalDate date) {
        try {
            String json = redis.opsForValue().get(key(currency, date));
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, CachedRate.class).toDomain());
        } catch (DataAccessException | JacksonException | IllegalArgumentException e) {
            log.warn("Exchange rate cache read failed, using the database: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(ExchangeRate rate) {
        try {
            redis.opsForValue().set(
                    key(rate.currency(), rate.rateDate()), mapper.writeValueAsString(CachedRate.from(rate)), ttl);
        } catch (DataAccessException | JacksonException e) {
            log.warn("Exchange rate cache write failed: {}", e.getMessage());
        }
    }

    static String key(Currency currency, LocalDate date) {
        return KEY_PREFIX + currency.getCurrencyCode() + ":" + date;
    }

    record CachedRate(String currency, LocalDate rateDate, BigDecimal unitsPerUsd, LocalDate sourceDate, RateKind kind) {

        static CachedRate from(ExchangeRate rate) {
            return new CachedRate(rate.currency().getCurrencyCode(), rate.rateDate(), rate.unitsPerUsd(),
                    rate.sourceDate(), rate.kind());
        }

        ExchangeRate toDomain() {
            return new ExchangeRate(Currency.getInstance(currency), rateDate, unitsPerUsd, sourceDate, kind);
        }
    }
}
