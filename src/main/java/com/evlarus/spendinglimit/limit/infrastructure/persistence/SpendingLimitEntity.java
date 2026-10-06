package com.evlarus.spendinglimit.limit.infrastructure.persistence;

import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/** Persistence model of {@code spending_limit}. Business rules live in the domain {@code SpendingLimit}. */
@Entity
@Table(name = "spending_limit")
@Immutable
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpendingLimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account", nullable = false, length = 10)
    private String account;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_category", nullable = false, length = 16)
    private ExpenseCategory expenseCategory;

    @Column(name = "limit_sum", nullable = false, precision = 19, scale = 2)
    private BigDecimal limitSum;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "limit_datetime", nullable = false)
    private Instant limitDatetime;

    @Column(name = "is_default", nullable = false)
    private boolean systemDefault;
}
