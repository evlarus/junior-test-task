package com.evlarus.spendinglimit.common.domain;

/** The request is valid but clashes with the current state (HTTP 409); other domain exceptions mean HTTP 422. */
public abstract class ConflictException extends DomainException {

    protected ConflictException(String message) {
        super(message);
    }
}
