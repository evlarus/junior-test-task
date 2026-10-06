package com.evlarus.spendinglimit.limit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persistence model of {@code monthly_spending}. Business rules live in the domain {@code MonthlySpending}. */
@Entity
@Table(name = "monthly_spending")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlySpendingEntity {

    @EmbeddedId
    private MonthlySpendingKey key;

    @Column(name = "spent_usd", nullable = false, precision = 19, scale = 2)
    private BigDecimal spentUsd;
}
