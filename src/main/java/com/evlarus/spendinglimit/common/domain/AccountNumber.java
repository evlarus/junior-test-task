package com.evlarus.spendinglimit.common.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Bank account number: exactly 10 digits, leading zeros are significant.
 *
 * <p>{@link #toString()} is masked so that account numbers never end up in logs in full.
 */
public record AccountNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("\\d{10}");

    public AccountNumber {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Account number must consist of exactly 10 digits");
        }
    }

    public static AccountNumber of(String value) {
        return new AccountNumber(value);
    }

    /** Only the last four digits are visible: {@code ******0123}. */
    public String masked() {
        return "******" + value.substring(value.length() - 4);
    }

    @Override
    public String toString() {
        return masked();
    }
}
