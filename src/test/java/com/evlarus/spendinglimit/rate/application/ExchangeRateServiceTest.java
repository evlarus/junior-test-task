package com.evlarus.spendinglimit.rate.application;

import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static com.evlarus.spendinglimit.support.DomainFixtures.RUB;
import static com.evlarus.spendinglimit.support.DomainFixtures.UTC_CALENDAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evlarus.spendinglimit.rate.domain.DailyClose;
import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.rate.domain.ExchangeRateRepository;
import com.evlarus.spendinglimit.rate.domain.RateKind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExchangeRateServiceTest {

    private static final LocalDate FRIDAY = LocalDate.of(2022, 1, 7);
    private static final LocalDate SATURDAY = FRIDAY.plusDays(1);
    private static final LocalDate MONDAY = FRIDAY.plusDays(3);
    private static final DailyClose FRIDAY_CLOSE = new DailyClose(KZT, FRIDAY, new BigDecimal("431.8"));

    private final ExchangeRateRepository repository = mock(ExchangeRateRepository.class);
    private final ExchangeRateCache cache = mock(ExchangeRateCache.class);
    private final ExchangeRateProvider provider = mock(ExchangeRateProvider.class);

    private ExchangeRateService serviceAt(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        RatesProperties properties = new RatesProperties(
                RatesProperties.Provider.FIXED, 10, Set.of(KZT, RUB),
                new RatesProperties.TwelveData(null), new RatesProperties.Fixed(Map.of()));
        return new ExchangeRateService(repository, cache, provider, UTC_CALENDAR, clock, properties, new SimpleMeterRegistry());
    }

    @Test
    void cachedRateIsUsedWithoutTheDatabaseOrTheProvider() {
        ExchangeRate cached = new ExchangeRate(KZT, FRIDAY, new BigDecimal("431.8"), FRIDAY, RateKind.CLOSE);
        when(cache.get(KZT, FRIDAY)).thenReturn(Optional.of(cached));

        assertThat(serviceAt(MONDAY).findRate(KZT, FRIDAY)).isEqualTo(new RateLookup.Found(cached));
        verify(repository, never()).find(any(), any());
        verify(provider, never()).dailyCloses(any(), any(), any());
    }

    @Test
    void storedRateIsUsedWithoutAskingTheProviderAndIsCached() {
        ExchangeRate stored = new ExchangeRate(KZT, FRIDAY, new BigDecimal("431.8"), FRIDAY, RateKind.CLOSE);
        when(repository.find(KZT, FRIDAY)).thenReturn(Optional.of(stored));

        assertThat(serviceAt(MONDAY).findRate(KZT, FRIDAY)).isEqualTo(new RateLookup.Found(stored));
        verify(provider, never()).dailyCloses(any(), any(), any());
        verify(cache).put(stored);
    }

    @Test
    void fetchedRateIsCachedOnceItIsStored() {
        ExchangeRate stored = new ExchangeRate(KZT, FRIDAY, new BigDecimal("431.8"), FRIDAY, RateKind.CLOSE);
        when(repository.find(KZT, FRIDAY)).thenReturn(Optional.empty(), Optional.of(stored));
        when(provider.dailyCloses(KZT, FRIDAY.minusDays(10), FRIDAY)).thenReturn(List.of(FRIDAY_CLOSE));

        serviceAt(MONDAY).findRate(KZT, FRIDAY);

        verify(cache).put(stored);
    }

    @Test
    void missingRateIsFetchedWithALookbackWindowAndStored() {
        when(repository.find(KZT, FRIDAY)).thenReturn(Optional.empty());
        when(provider.dailyCloses(KZT, FRIDAY.minusDays(10), FRIDAY)).thenReturn(List.of(FRIDAY_CLOSE));

        serviceAt(MONDAY).findRate(KZT, FRIDAY);

        assertThat(storedRates()).containsExactly(
                new ExchangeRate(KZT, FRIDAY, new BigDecimal("431.8"), FRIDAY, RateKind.CLOSE));
    }

    @Test
    void previousCloseOfAPastWeekendIsStoredForTheWeekendDay() {
        when(repository.find(KZT, SATURDAY)).thenReturn(Optional.empty());
        when(provider.dailyCloses(eq(KZT), any(), eq(SATURDAY))).thenReturn(List.of(FRIDAY_CLOSE));

        RateLookup lookup = serviceAt(MONDAY).findRate(KZT, SATURDAY);

        ExchangeRate previousClose = new ExchangeRate(KZT, SATURDAY, new BigDecimal("431.8"), FRIDAY, RateKind.PREVIOUS_CLOSE);
        assertThat(lookup).isEqualTo(new RateLookup.Found(previousClose));
        assertThat(storedRates()).contains(previousClose);
    }

    @Test
    void previousCloseOfTodayIsUsedButNotStoredBecauseTodaysCloseMayStillAppear() {
        when(repository.find(KZT, SATURDAY)).thenReturn(Optional.empty());
        when(provider.dailyCloses(eq(KZT), any(), eq(SATURDAY))).thenReturn(List.of(FRIDAY_CLOSE));

        RateLookup lookup = serviceAt(SATURDAY).findRate(KZT, SATURDAY);

        assertThat(lookup).isInstanceOf(RateLookup.Found.class);
        assertThat(storedRates()).noneMatch(rate -> rate.rateDate().equals(SATURDAY));
        verify(cache, never()).put(any());
    }

    @Test
    void providerFailureIsReportedAsUnavailable() {
        when(repository.find(KZT, FRIDAY)).thenReturn(Optional.empty());
        when(provider.dailyCloses(any(), any(), any())).thenThrow(new RateProviderException("down", null));

        assertThat(serviceAt(MONDAY).findRate(KZT, FRIDAY))
                .isEqualTo(new RateLookup.Unavailable("down"));
        verify(repository, never()).addAllIfAbsent(anyCollection());
    }

    @Test
    void noCloseWithinTheLookbackIsUnavailable() {
        when(repository.find(KZT, FRIDAY)).thenReturn(Optional.empty());
        when(provider.dailyCloses(any(), any(), any())).thenReturn(List.of());

        assertThat(serviceAt(MONDAY).findRate(KZT, FRIDAY)).isInstanceOf(RateLookup.Unavailable.class);
    }

    @Test
    void prefetchAsksForEverySupportedCurrency() {
        when(repository.find(any(), eq(FRIDAY))).thenReturn(Optional.empty());
        when(provider.dailyCloses(any(), any(), any())).thenReturn(List.of());

        serviceAt(FRIDAY).prefetch(FRIDAY);

        verify(provider, times(1)).dailyCloses(eq(KZT), any(), eq(FRIDAY));
        verify(provider, times(1)).dailyCloses(eq(RUB), any(), eq(FRIDAY));
    }

    @SuppressWarnings("unchecked")
    private Collection<ExchangeRate> storedRates() {
        ArgumentCaptor<Collection<ExchangeRate>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).addAllIfAbsent(captor.capture());
        return captor.getValue();
    }
}
