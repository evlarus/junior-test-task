package com.evlarus.spendinglimit.transaction.infrastructure.persistence;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.transaction.application.ExceededTransaction;
import com.evlarus.spendinglimit.transaction.application.ExceededTransactionsQuery;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaExceededTransactionsQuery implements ExceededTransactionsQuery {

    private static final String EXCEEDED_WITH_LIMIT = """
            select new com.evlarus.spendinglimit.transaction.infrastructure.persistence.ExceededTransactionRow(
                       t.accountFrom, t.accountTo, t.currency, t.amount, t.expenseCategory,
                       t.occurredAt, t.occurredOffsetSeconds,
                       l.limitSum, l.currency, l.limitDatetime)
            from TransactionEntity t
                     join t.limit l
            where t.accountFrom = :account
              and t.limitExceeded = true
            order by t.occurredAt, t.id
            """;

    private final EntityManager entityManager;

    JpaExceededTransactionsQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExceededTransaction> findByAccount(AccountNumber account, int page, int size) {
        return entityManager.createQuery(EXCEEDED_WITH_LIMIT, ExceededTransactionRow.class)
                .setParameter("account", account.value())
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList()
                .stream()
                .map(ExceededTransactionRow::toView)
                .toList();
    }
}
