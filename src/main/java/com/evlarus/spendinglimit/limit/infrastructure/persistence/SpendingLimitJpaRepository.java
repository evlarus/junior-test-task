package com.evlarus.spendinglimit.limit.infrastructure.persistence;

import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SpendingLimitJpaRepository extends JpaRepository<SpendingLimitEntity, Long> {

    /** Served by the partial index {@code ux_spending_limit_client_moment}: one index range scan, no sort. */
    @Query("""
            select l from SpendingLimitEntity l
            where l.account = :account
              and l.expenseCategory = :category
              and l.systemDefault = false
              and l.limitDatetime <= :moment
            order by l.limitDatetime desc
            limit 1
            """)
    Optional<SpendingLimitEntity> findLatestClientLimit(String account, ExpenseCategory category, Instant moment);

    Optional<SpendingLimitEntity> findByAccountAndExpenseCategoryAndSystemDefaultTrue(
            String account, ExpenseCategory expenseCategory);

    /** JPA has no "insert if absent"; the partial unique index makes concurrent inserts collapse into one row. */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into spending_limit (account, expense_category, limit_sum, currency, limit_datetime, is_default)
            values (:account, :category, :limitSum, 'USD', :limitDatetime, true)
            on conflict (account, expense_category) where is_default do nothing
            """)
    void insertSystemDefaultIfAbsent(String account, String category, BigDecimal limitSum, Instant limitDatetime);
}
