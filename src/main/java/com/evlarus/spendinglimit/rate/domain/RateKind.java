package com.evlarus.spendinglimit.rate.domain;

/** Where a rate for a day comes from. */
public enum RateKind {

    /** Closing rate of that very day. */
    CLOSE,

    /** No trading that day (weekend, holiday): closing rate of the last trading day before it. */
    PREVIOUS_CLOSE
}
