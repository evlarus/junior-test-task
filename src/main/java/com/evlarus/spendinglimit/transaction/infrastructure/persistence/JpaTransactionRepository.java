package com.evlarus.spendinglimit.transaction.infrastructure.persistence;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.infrastructure.persistence.SpendingLimitEntity;
import com.evlarus.spendinglimit.transaction.domain.ProcessingResult;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionRepository;
import com.evlarus.spendinglimit.transaction.domain.TransactionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaTransactionRepository implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;
    private final EntityManager entityManager;

    JpaTransactionRepository(TransactionJpaRepository jpaRepository, EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Transaction add(Transaction transaction) {
        if (transaction.id() != null || !transaction.isPending()) {
            throw new IllegalArgumentException("Only a new pending transaction can be added");
        }
        TransactionEntity entity = new TransactionEntity();
        entity.setAccountFrom(transaction.accountFrom().value());
        entity.setAccountTo(transaction.accountTo().value());
        entity.setCurrency(transaction.amount().currency().getCurrencyCode());
        entity.setAmount(transaction.amount().amount());
        entity.setExpenseCategory(transaction.category());
        entity.setOccurredAt(transaction.occurredAt().toInstant());
        entity.setOccurredOffsetSeconds(transaction.occurredAt().getOffset().getTotalSeconds());
        entity.setReceivedAt(transaction.receivedAt());
        entity.setStatus(transaction.status());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transaction> findById(long id) {
        return jpaRepository.findById(id).map(JpaTransactionRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> findAllById(Collection<Long> ids) {
        return jpaRepository.findAllById(ids).stream().map(JpaTransactionRepository::toDomain).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Transaction> lockById(long id) {
        return jpaRepository.findByIdForUpdate(id).map(JpaTransactionRepository::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveProcessingResult(Transaction transaction) {
        Long id = transaction.id();
        ProcessingResult result = transaction.result()
                .orElseThrow(() -> new IllegalArgumentException("Transaction %d is not processed".formatted(id)));
        TransactionEntity entity = id == null ? null : entityManager.find(TransactionEntity.class, id);
        if (entity == null || entityManager.getLockMode(entity) != LockModeType.PESSIMISTIC_WRITE) {
            throw new IllegalStateException("Transaction must be locked with lockById in this transaction");
        }
        applyResult(entity, result);
    }

    private void applyResult(TransactionEntity entity, ProcessingResult result) {
        entity.setStatus(TransactionStatus.PROCESSED);
        entity.setAmountUsd(result.amountUsd().amount());
        // A reference only sets the FK; it does not load the limit
        entity.setLimit(entityManager.getReference(SpendingLimitEntity.class, result.limitId()));
        // Read-only mirror of the FK: Hibernate does not write it, but keep it consistent for reads in this session
        entity.setLimitId(result.limitId());
        entity.setLimitExceeded(result.limitExceeded());
        entity.setProcessedAt(result.processedAt());
    }

    private static Transaction toDomain(TransactionEntity entity) {
        ProcessingResult result = entity.getStatus() == TransactionStatus.PROCESSED
                ? new ProcessingResult(
                        new Money(entity.getAmountUsd(), Money.USD),
                        entity.getLimitId(),
                        entity.getLimitExceeded(),
                        entity.getProcessedAt())
                : null;
        return new Transaction(
                entity.getId(),
                AccountNumber.of(entity.getAccountFrom()),
                AccountNumber.of(entity.getAccountTo()),
                new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency())),
                entity.getExpenseCategory(),
                entity.getOccurredAt().atOffset(ZoneOffset.ofTotalSeconds(entity.getOccurredOffsetSeconds())),
                entity.getReceivedAt(),
                result);
    }
}
