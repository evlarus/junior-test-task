package com.evlarus.spendinglimit.limit.infrastructure.persistence;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.application.LimitUsage;
import com.evlarus.spendinglimit.limit.application.LimitUsageQuery;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limits with usage in one statement: a JOIN with a subquery that aggregates the transactions of the account's
 * limits by {@code limit_id} (COUNT, COUNT FILTER, SUM, GROUP BY). The subquery selects transactions through
 * the account's limit ids, so it is served by the index on {@code bank_transaction.limit_id}.
 */
@Repository
class JdbcLimitUsageQuery implements LimitUsageQuery {

    private static final String LIMITS_WITH_USAGE = """
            select l.id,
                   l.expense_category,
                   l.limit_sum,
                   l.currency,
                   l.limit_datetime,
                   l.is_default,
                   coalesce(usage.transactions, 0)          as transactions,
                   coalesce(usage.exceeded_transactions, 0) as exceeded_transactions,
                   coalesce(usage.spent_usd, 0)             as spent_usd
            from spending_limit l
                     left join (select t.limit_id,
                                       count(*)                                 as transactions,
                                       count(*) filter (where t.limit_exceeded) as exceeded_transactions,
                                       sum(t.amount_usd)                        as spent_usd
                                from bank_transaction t
                                where t.limit_id in (select id from spending_limit where account = :account)
                                group by t.limit_id) usage on usage.limit_id = l.id
            where l.account = :account
            order by l.expense_category, l.limit_datetime desc
            """;

    private final JdbcClient jdbc;

    JdbcLimitUsageQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LimitUsage> findByAccount(AccountNumber account) {
        return jdbc.sql(LIMITS_WITH_USAGE)
                .param("account", account.value())
                .query((row, rowNumber) -> toUsage(account, row))
                .list();
    }

    private static LimitUsage toUsage(AccountNumber account, ResultSet row) throws SQLException {
        SpendingLimit limit = new SpendingLimit(
                row.getLong("id"),
                account,
                ExpenseCategory.valueOf(row.getString("expense_category")),
                new Money(row.getBigDecimal("limit_sum"), Currency.getInstance(row.getString("currency"))),
                row.getObject("limit_datetime", OffsetDateTime.class).toInstant(),
                row.getBoolean("is_default"));
        return new LimitUsage(
                limit,
                row.getLong("transactions"),
                row.getLong("exceeded_transactions"),
                new Money(row.getBigDecimal("spent_usd"), Money.USD));
    }
}
