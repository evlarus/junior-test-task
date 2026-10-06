package com.evlarus.spendinglimit.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * Counts SQL statements prepared by Hibernate, to prove that a read does not trigger N+1 queries.
 * Statistics are global, so tests that use it must not run in parallel with other database tests.
 */
public final class QueryCounter {

    private final Statistics statistics;

    public QueryCounter(EntityManagerFactory entityManagerFactory) {
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    public void reset() {
        statistics.clear();
    }

    public long statements() {
        return statistics.getPrepareStatementCount();
    }
}
