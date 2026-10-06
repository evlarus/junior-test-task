package com.evlarus.spendinglimit.rate.infrastructure.persistence;

import com.evlarus.spendinglimit.rate.domain.RateKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Read model of {@code exchange_rate}; rows are inserted with a batch upsert in {@link JpaExchangeRateRepository}. */
@Entity
@Table(name = "exchange_rate")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRateEntity {

    @Id
    private Long id;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "units_per_usd", nullable = false, precision = 19, scale = 8)
    private BigDecimal unitsPerUsd;

    @Column(name = "source_date", nullable = false)
    private LocalDate sourceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private RateKind kind;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
