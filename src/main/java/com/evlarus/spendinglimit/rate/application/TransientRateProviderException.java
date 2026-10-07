package com.evlarus.spendinglimit.rate.application;

import org.jspecify.annotations.Nullable;

/**
 * A failure that usually goes away within moments (server error, rate limiting, refused connection),
 * so the call is retried immediately a few times. A timeout is deliberately not transient: a slow provider
 * would make every retry as slow, and the caller would wait several timeouts in a row.
 */
public class TransientRateProviderException extends RateProviderException {

    public TransientRateProviderException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
