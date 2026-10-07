package com.evlarus.spendinglimit.limit.application;

import static com.evlarus.spendinglimit.support.DomainFixtures.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.limit.domain.SpendingLimitRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LimitServiceTest {

    private static final Instant NOW = Instant.parse("2022-01-10T09:30:00.123456Z");

    private final SpendingLimitRepository repository = mock(SpendingLimitRepository.class);
    private final LimitService service =
            new LimitService(repository, mock(LimitUsageQuery.class), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void newLimitIsSetByTheClientAndTakesEffectAtTheCurrentMoment() {
        when(repository.add(any())).thenAnswer(call -> call.getArgument(0));

        SpendingLimit limit = service.setLimit(CLIENT, ExpenseCategory.PRODUCT, Money.usd("2000"));

        assertThat(limit.setAt()).isEqualTo(NOW);
        assertThat(limit.systemDefault()).isFalse();
        assertThat(limit.amount()).isEqualTo(Money.usd("2000"));
    }
}
