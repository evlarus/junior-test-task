package com.evlarus.spendinglimit.limit.infrastructure.persistence;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import com.evlarus.spendinglimit.limit.domain.SpendingLimitRepository;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaSpendingLimitRepository implements SpendingLimitRepository {

    private final SpendingLimitJpaRepository jpaRepository;

    JpaSpendingLimitRepository(SpendingLimitJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public SpendingLimit add(SpendingLimit limit) {
        if (limit.isPersisted()) {
            throw new IllegalArgumentException("Spending limit %d is already saved; limits are immutable"
                    .formatted(limit.id()));
        }
        return toDomain(jpaRepository.save(toEntity(limit)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SpendingLimit> findLatestClientLimit(
            AccountNumber account, ExpenseCategory category, Instant moment) {
        return jpaRepository.findLatestClientLimit(account.value(), category, moment).map(JpaSpendingLimitRepository::toDomain);
    }

    @Override
    @Transactional
    public SpendingLimit findOrCreateSystemDefault(SpendingLimit candidate) {
        if (!candidate.systemDefault() || candidate.isPersisted()) {
            throw new IllegalArgumentException("Candidate must be a new system default limit");
        }
        jpaRepository.insertSystemDefaultIfAbsent(
                candidate.account().value(),
                candidate.category().name(),
                candidate.amount().amount(),
                candidate.setAt());
        return jpaRepository
                .findByAccountAndExpenseCategoryAndSystemDefaultTrue(candidate.account().value(), candidate.category())
                .map(JpaSpendingLimitRepository::toDomain)
                .orElseThrow(() -> new IllegalStateException("System default limit was not stored"));
    }

    static SpendingLimit toDomain(SpendingLimitEntity entity) {
        return new SpendingLimit(
                entity.getId(),
                AccountNumber.of(entity.getAccount()),
                entity.getExpenseCategory(),
                new Money(entity.getLimitSum(), Currency.getInstance(entity.getCurrency())),
                entity.getLimitDatetime(),
                entity.isSystemDefault());
    }

    private static SpendingLimitEntity toEntity(SpendingLimit limit) {
        SpendingLimitEntity entity = new SpendingLimitEntity();
        entity.setAccount(limit.account().value());
        entity.setExpenseCategory(limit.category());
        entity.setLimitSum(limit.amount().amount());
        entity.setCurrency(limit.amount().currency().getCurrencyCode());
        entity.setLimitDatetime(limit.setAt());
        entity.setSystemDefault(limit.systemDefault());
        return entity;
    }
}
