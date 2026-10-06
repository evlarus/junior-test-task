package com.evlarus.spendinglimit.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountNumberTest {

    @Test
    void keepsLeadingZeros() {
        assertThat(AccountNumber.of("0000000123").value()).isEqualTo("0000000123");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "123",
        "12345678901",
        "00000001a3",
        " 000000123",
        "١٢٣٤٥٦٧٨٩٠" // Arabic-Indic digits are digits for Unicode, but not a valid account number
    })
    void rejectsAnythingButTenAsciiDigits(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> AccountNumber.of(value));
    }

    @Test
    void neverPrintsFullNumber() {
        AccountNumber account = AccountNumber.of("1234560123");

        assertThat(account.masked()).isEqualTo("******0123");
        assertThat(account).hasToString("******0123");
    }
}
