package com.evlarus.spendinglimit.limit.infrastructure.persistence;

import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.time.LocalDate;

/** Composite primary key of {@code monthly_spending}; a record gives correct equals/hashCode for free. */
@Embeddable
public record MonthlySpendingKey(
        @Column(name = "account", nullable = false, length = 10)
        String account,

        @Enumerated(EnumType.STRING)
        @Column(name = "expense_category", nullable = false, length = 16)
        ExpenseCategory expenseCategory,

        @Column(name = "month_start", nullable = false)
        LocalDate monthStart) implements Serializable {
}
