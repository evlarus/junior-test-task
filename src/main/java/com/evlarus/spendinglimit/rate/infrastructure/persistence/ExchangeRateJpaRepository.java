package com.evlarus.spendinglimit.rate.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExchangeRateJpaRepository extends JpaRepository<ExchangeRateEntity, Long> {

    Optional<ExchangeRateEntity> findByCurrencyAndRateDate(String currency, LocalDate rateDate);
}
