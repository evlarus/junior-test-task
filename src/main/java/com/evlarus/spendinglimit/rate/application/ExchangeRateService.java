package com.evlarus.spendinglimit.rate.application;

import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.rate.domain.DailyClose;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.rate.domain.ExchangeRateRepository;
import com.evlarus.spendinglimit.rate.domain.RateKind;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Rates for converting transaction amounts to USD. The database is the primary source; the external provider is
 * asked only for a day that is not stored yet, and everything it returns is stored, so each day is paid for once.
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateRepository repository;
    private final ExchangeRateProvider provider;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final RatesProperties properties;
    private final MeterRegistry meterRegistry;

    /** Requests to the provider currently running, by currency and day; concurrent misses share one request. */
    private final ConcurrentMap<RateKey, CompletableFuture<RateLookup>> inFlight = new ConcurrentHashMap<>();

    public ExchangeRateService(
            ExchangeRateRepository repository,
            ExchangeRateProvider provider,
            BusinessCalendar calendar,
            Clock clock,
            RatesProperties properties,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.provider = provider;
        this.calendar = calendar;
        this.clock = clock;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * The rate for converting amounts of {@code currency} made on {@code date} (a business day) to USD:
     * the close of that day, or the previous close when there was no trading.
     */
    public RateLookup findRate(Currency currency, LocalDate date) {
        if (currency.equals(Money.USD)) {
            return new RateLookup.Found(ExchangeRate.usd(date));
        }
        Optional<ExchangeRate> stored = repository.find(currency, date);
        if (stored.isPresent()) {
            log.debug("Using the stored {} rate for {}", currency, date);
            count("stored");
            return new RateLookup.Found(stored.get());
        }
        return singleFlight(new RateKey(currency, date), () -> fetchAndStore(currency, date));
    }

    /** Fetches the rates of all supported currencies for {@code date} in parallel, one virtual thread each. */
    public void prefetch(LocalDate date) {
        List<Currency> currencies = properties.supportedCurrencies().stream()
                .filter(currency -> !currency.equals(Money.USD))
                .toList();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<RateLookup>> lookups = currencies.stream()
                    .map(currency -> CompletableFuture.supplyAsync(() -> findRate(currency, date), executor))
                    .toList();
            CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).join();
            for (int i = 0; i < currencies.size(); i++) {
                log.info("Prefetched {} rate for {}: {}", currencies.get(i), date, lookups.get(i).join());
            }
        }
    }

    private RateLookup fetchAndStore(Currency currency, LocalDate date) {
        List<DailyClose> closes;
        try {
            closes = provider.dailyCloses(currency, date.minusDays(properties.lookbackDays()), date);
        } catch (RateProviderException e) {
            log.warn("Exchange rate {} for {} is unavailable: {}", currency, date, e.getMessage());
            count("unavailable");
            return new RateLookup.Unavailable(e.getMessage());
        }

        List<ExchangeRate> toStore = new ArrayList<>(closes.stream()
                .filter(close -> close.currency().equals(currency) && !close.date().isAfter(date))
                .map(close -> new ExchangeRate(currency, close.date(), close.unitsPerUsd(), close.date(), RateKind.CLOSE))
                .toList());
        Optional<ExchangeRate> selected = ExchangeRate.select(currency, date, closes);
        if (selected.isEmpty()) {
            repository.addAllIfAbsent(toStore);
            count("unavailable");
            return new RateLookup.Unavailable("No close of %s on or within %d days before %s"
                    .formatted(currency, properties.lookbackDays(), date));
        }

        ExchangeRate rate = selected.get();
        // A previous close stands in for a past day without trading for good; for today a close may still appear
        boolean today = date.equals(calendar.dateOf(clock.instant()));
        if (rate.kind() == RateKind.PREVIOUS_CLOSE && today) {
            repository.addAllIfAbsent(toStore);
            count("fetched");
            return new RateLookup.Found(rate);
        }
        if (rate.kind() == RateKind.PREVIOUS_CLOSE) {
            log.info("No trading in {} on {}, using the close of {}", currency, date, rate.sourceDate());
            toStore.add(rate);
        }
        repository.addAllIfAbsent(toStore);
        count("fetched");
        // If a concurrent writer stored this day first, its rate wins: every transaction of a day uses one rate
        return new RateLookup.Found(repository.find(currency, date).orElse(rate));
    }

    private RateLookup singleFlight(RateKey key, Supplier<RateLookup> loader) {
        CompletableFuture<RateLookup> mine = new CompletableFuture<>();
        CompletableFuture<RateLookup> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            return await(running);
        }
        try {
            RateLookup result = loader.get();
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private static RateLookup await(CompletableFuture<RateLookup> running) {
        try {
            return running.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    private void count(String source) {
        meterRegistry.counter("exchange_rate.lookups", "source", source).increment();
    }

    private record RateKey(Currency currency, LocalDate date) {
    }
}
