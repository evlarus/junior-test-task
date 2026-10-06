package com.evlarus.spendinglimit.transaction.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface TransactionJpaRepository extends JpaRepository<TransactionEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransactionEntity t where t.id = :id")
    Optional<TransactionEntity> findByIdForUpdate(long id);
}
