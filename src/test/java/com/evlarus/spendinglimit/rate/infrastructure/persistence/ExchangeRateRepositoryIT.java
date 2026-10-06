package com.evlarus.spendinglimit.rate.infrastructure.persistence;

import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static org.assertj.core.api.Assertions.assertThat;

import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.rate.domain.ExchangeRateRepository;
import com.evlarus.spendinglimit.rate.domain.RateKind;
import com.evlarus.spendinglimit.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ExchangeRateRepositoryIT {

    /** Rates are unique per currency and day; every test works with days no other test uses. */
    private static final AtomicInteger NEXT_WEEK = new AtomicInteger();

    @Autowired
    private ExchangeRateRepository repository;

    private final LocalDate friday = LocalDate.of(1990, 1, 5).plusWeeks(NEXT_WEEK.getAndIncrement());

    @Test
    void storesAndFindsRatesOfDifferentKinds() {
        ExchangeRate close = new ExchangeRate(KZT, friday, new BigDecimal("431.8"), friday, RateKind.CLOSE);
        ExchangeRate weekend = new ExchangeRate(KZT, friday.plusDays(1), new BigDecimal("431.8"), friday, RateKind.PREVIOUS_CLOSE);

        repository.addAllIfAbsent(List.of(close, weekend));

        assertThat(repository.find(KZT, friday)).contains(close);
        assertThat(repository.find(KZT, friday.plusDays(1))).contains(weekend);
        assertThat(repository.find(KZT, friday.plusDays(2))).isEmpty();
    }

    @Test
    void keepsTheRateThatWasStoredFirst() {
        ExchangeRate first = new ExchangeRate(KZT, friday, new BigDecimal("450"), friday, RateKind.CLOSE);
        ExchangeRate later = new ExchangeRate(KZT, friday, new BigDecimal("500"), friday, RateKind.CLOSE);

        repository.addAllIfAbsent(List.of(first));
        repository.addAllIfAbsent(List.of(later));

        assertThat(repository.find(KZT, friday)).contains(first);
    }

    @Test
    void emptyBatchDoesNothing() {
        repository.addAllIfAbsent(List.of());

        assertThat(repository.find(KZT, friday)).isEmpty();
    }
}
