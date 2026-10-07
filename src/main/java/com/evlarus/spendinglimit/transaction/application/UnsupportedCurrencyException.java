package com.evlarus.spendinglimit.transaction.application;

import com.evlarus.spendinglimit.common.domain.DomainException;
import java.util.Currency;

public class UnsupportedCurrencyException extends DomainException {

    public UnsupportedCurrencyException(Currency currency) {
        super("Currency %s is not supported".formatted(currency.getCurrencyCode()));
    }
}
