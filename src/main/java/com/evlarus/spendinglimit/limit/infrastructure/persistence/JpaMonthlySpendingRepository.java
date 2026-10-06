package com.evlarus.spendinglimit.limit.infrastructure.persistence;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.MonthlySpending;
import com.evlarus.spendinglimit.limit.domain.MonthlySpendingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.YearMonth;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Both methods require a surrounding transaction ({@link Propagation#MANDATORY}): a row lock taken outside
 * of one would be released immediately and protect nothing.
 */
@Repository
class JpaMonthlySpendingRepository implements MonthlySpendingRepository {

    private final MonthlySpendingJpaRepository jpaRepository;
    private final EntityManager entityManager;

    JpaMonthlySpendingRepository(MonthlySpendingJpaRepository jpaRepository, EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MonthlySpending lockOrCreate(AccountNumber account, ExpenseCategory category, YearMonth month) {
        MonthlySpendingKey key = new MonthlySpendingKey(account.value(), category, month.atDay(1));
        jpaRepository.insertIfAbsent(key.account(), key.expenseCategory().name(), key.monthStart());
        // SELECT ... FOR UPDATE: waits until a concurrent transaction holding the row commits, then reads its total
        MonthlySpendingEntity entity = entityManager.find(MonthlySpendingEntity.class, key, LockModeType.PESSIMISTIC_WRITE);
        if (entity == null) {
            throw new IllegalStateException("Monthly spending row was not created");
        }
        return new MonthlySpending(account, category, month, new Money(entity.getSpentUsd(), Money.USD));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(MonthlySpending spending) {
        MonthlySpendingKey key = new MonthlySpendingKey(
                spending.account().value(), spending.category(), spending.month().atDay(1));
        MonthlySpendingEntity entity = entityManager.find(MonthlySpendingEntity.class, key);
        if (entity == null || entityManager.getLockMode(entity) != LockModeType.PESSIMISTIC_WRITE) {
            throw new IllegalStateException("Monthly spending must be locked with lockOrCreate in this transaction");
        }
        entity.setSpentUsd(spending.spent().amount());
    }
}
