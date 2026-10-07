package com.evlarus.spendinglimit.rate.application;

import org.jspecify.annotations.Nullable;

/** The rate provider could not deliver rates. Transactions waiting for the rate stay pending and are retried later. */
public class RateProviderException extends RuntimeException {

    public RateProviderException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
