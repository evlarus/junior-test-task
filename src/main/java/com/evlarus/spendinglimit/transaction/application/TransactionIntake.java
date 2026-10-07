package com.evlarus.spendinglimit.transaction.application;

import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.rate.application.RatesProperties;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Currency;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TransactionIntake {

    private static final Logger log = LoggerFactory.getLogger(TransactionIntake.class);

    private final TransactionRepository transactions;
    private final ProcessingAttempts attempts;
    private final Set<Currency> supportedCurrencies;
    private final Duration allowedClockSkew;
    private final Clock clock;

    public TransactionIntake(
            TransactionRepository transactions,
            ProcessingAttempts attempts,
            RatesProperties ratesProperties,
            TransactionsProperties transactionsProperties,
            Clock clock) {
        this.transactions = transactions;
        this.attempts = attempts;
        this.supportedCurrencies = Set.copyOf(ratesProperties.supportedCurrencies());
        this.allowedClockSkew = transactionsProperties.allowedClockSkew();
        this.clock = clock;
    }

    public Transaction receive(ReceiveTransaction command) {
        Currency currency = command.amount().currency();
        if (!currency.equals(Money.USD) && !supportedCurrencies.contains(currency)) {
            throw new UnsupportedCurrencyException(currency);
        }
        Transaction received = Transaction.receive(
                command.accountFrom(),
                command.accountTo(),
                command.amount(),
                command.category(),
                command.occurredAt(),
                clock.instant(),
                allowedClockSkew);
        Transaction saved = transactions.add(received);
        log.info("Transaction {} of account {} received: {} {}",
                saved.id(), saved.accountFrom(), saved.amount(), saved.category());
        return attempts.attempt(saved, 0);
    }
}
