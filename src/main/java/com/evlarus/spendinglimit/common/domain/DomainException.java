package com.evlarus.spendinglimit.common.domain;

/**
 * Violation of a business rule that a client can cause with a valid request
 * (as opposed to {@link IllegalArgumentException}, which signals a programming error).
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
