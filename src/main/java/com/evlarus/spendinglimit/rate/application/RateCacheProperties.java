package com.evlarus.spendinglimit.rate.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.rates.cache")
@Validated
public record RateCacheProperties(@NotNull Type type, @NotNull Duration ttl) {

    public enum Type {
        NONE,
        REDIS
    }
}
