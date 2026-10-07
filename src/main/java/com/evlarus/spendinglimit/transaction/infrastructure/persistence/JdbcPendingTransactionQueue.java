package com.evlarus.spendinglimit.transaction.infrastructure.persistence;

import com.evlarus.spendinglimit.transaction.application.PendingTransactionQueue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcPendingTransactionQueue implements PendingTransactionQueue {

    private static final int MAX_ERROR_LENGTH = 500;

    private static final String CLAIM_DUE = """
            update bank_transaction
            set next_attempt_at = :leaseUntil
            where id in (select id
                         from bank_transaction
                         where status = 'PENDING'
                           and (next_attempt_at <= :now
                             or next_attempt_at is null and received_at <= :freshBefore)
                         order by received_at
                         limit :limit
                         for update skip locked)
            returning id, attempts
            """;

    private static final String SCHEDULE_RETRY = """
            update bank_transaction
            set attempts = attempts + 1,
                next_attempt_at = :nextAttemptAt,
                last_error = :reason
            where id = :id
              and status = 'PENDING'
            """;

    private final JdbcClient jdbc;

    JdbcPendingTransactionQueue(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public List<DueTransaction> claimDue(Instant now, Instant freshBefore, Instant leaseUntil, int limit) {
        return jdbc.sql(CLAIM_DUE)
                .param("now", utc(now))
                .param("freshBefore", utc(freshBefore))
                .param("leaseUntil", utc(leaseUntil))
                .param("limit", limit)
                .query((row, rowNumber) -> new DueTransaction(row.getLong("id"), row.getInt("attempts")))
                .list();
    }

    @Override
    @Transactional
    public void scheduleRetry(long transactionId, Instant nextAttemptAt, String reason) {
        jdbc.sql(SCHEDULE_RETRY)
                .param("id", transactionId)
                .param("nextAttemptAt", utc(nextAttemptAt))
                .param("reason", reason.length() > MAX_ERROR_LENGTH ? reason.substring(0, MAX_ERROR_LENGTH) : reason)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        return jdbc.sql("select count(*) from bank_transaction where status = 'PENDING'").query(Long.class).single();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
