package com.evlarus.spendinglimit.limit.infrastructure.persistence;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface MonthlySpendingJpaRepository extends JpaRepository<MonthlySpendingEntity, MonthlySpendingKey> {

    /** Creates an empty total; does nothing (and does not wait for its lock) when the row already exists. */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into monthly_spending (account, expense_category, month_start, spent_usd)
            values (:account, :category, :monthStart, 0)
            on conflict (account, expense_category, month_start) do nothing
            """)
    void insertIfAbsent(String account, String category, LocalDate monthStart);
}
