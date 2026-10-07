package com.evlarus.spendinglimit.transaction.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    /** Stores a newly received transaction and returns it with its id. */
    Transaction add(Transaction transaction);

    Optional<Transaction> findById(long id);

    List<Transaction> findAllById(Collection<Long> ids);

    /**
     * Returns the transaction locked until the current database transaction ends, so that it cannot be processed
     * twice concurrently. Must be called inside a transaction.
     */
    Optional<Transaction> lockById(long id);

    /** Stores the processing result of a transaction locked by {@link #lockById} in the same transaction. */
    void saveProcessingResult(Transaction transaction);
}
