package com.evlarus.spendinglimit.rate.infrastructure.persistence;

import com.evlarus.spendinglimit.rate.domain.ExchangeRate;
import com.evlarus.spendinglimit.rate.domain.ExchangeRateRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Currency;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaExchangeRateRepository implements ExchangeRateRepository {

    private static final String INSERT_IF_ABSENT = """
            insert into exchange_rate (currency, rate_date, units_per_usd, source_date, kind)
            values (:currency, :rateDate, :unitsPerUsd, :sourceDate, :kind)
            on conflict (currency, rate_date) do nothing
            """;

    private final ExchangeRateJpaRepository jpaRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    JpaExchangeRateRepository(ExchangeRateJpaRepository jpaRepository, NamedParameterJdbcTemplate jdbcTemplate) {
        this.jpaRepository = jpaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExchangeRate> find(Currency currency, LocalDate date) {
        return jpaRepository.findByCurrencyAndRateDate(currency.getCurrencyCode(), date)
                .map(JpaExchangeRateRepository::toDomain);
    }

    /** One JDBC batch for all rates: JPA has no "insert if absent", and one statement per rate would be O(N). */
    @Override
    @Transactional
    public void addAllIfAbsent(Collection<ExchangeRate> rates) {
        if (rates.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = rates.stream()
                .map(rate -> new MapSqlParameterSource()
                        .addValue("currency", rate.currency().getCurrencyCode())
                        .addValue("rateDate", rate.rateDate())
                        .addValue("unitsPerUsd", rate.unitsPerUsd())
                        .addValue("sourceDate", rate.sourceDate())
                        .addValue("kind", rate.kind().name()))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_IF_ABSENT, batch);
    }

    private static ExchangeRate toDomain(ExchangeRateEntity entity) {
        return new ExchangeRate(
                Currency.getInstance(entity.getCurrency()),
                entity.getRateDate(),
                entity.getUnitsPerUsd(),
                entity.getSourceDate(),
                entity.getKind());
    }
}
