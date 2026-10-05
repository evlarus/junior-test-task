package com.evlarus.spendinglimit.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the JSON contract configured in {@code application.yml}. Each of these settings
 * fails silently when misconfigured, so every one has a test.
 */
@JsonTest
class JsonContractTest {

    record Sample(String accountFrom, BigDecimal sum, OffsetDateTime datetime) {
    }

    @Autowired
    private JsonMapper mapper;

    @Test
    void usesSnakeCasePropertyNames() {
        String json = mapper.writeValueAsString(new Sample("0000000123", new BigDecimal("1.00"), null));

        assertThat(json).contains("\"account_from\":\"0000000123\"").doesNotContain("accountFrom");
    }

    @Test
    void rejectsUnknownProperties() {
        String json = """
                {"account_from": "0000000123", "limit_datetime": "2022-01-01T00:00:00Z"}
                """;

        assertThatThrownBy(() -> mapper.readValue(json, Sample.class))
                .isInstanceOf(UnrecognizedPropertyException.class)
                .hasMessageContaining("limit_datetime");
    }

    @Test
    void rejectsNumberWhereStringIsExpected() {
        // An account number sent as a number would lose leading zeros
        String json = """
                {"account_from": 123}
                """;

        assertThatThrownBy(() -> mapper.readValue(json, Sample.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void rejectsStringWhereNumberIsExpected() {
        String json = """
                {"sum": "10.00"}
                """;

        assertThatThrownBy(() -> mapper.readValue(json, Sample.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void readsDecimalWithoutPrecisionLoss() {
        String json = """
                {"sum": 10000.45}
                """;

        Sample sample = mapper.readValue(json, Sample.class);

        assertThat(sample.sum()).isEqualTo(new BigDecimal("10000.45"));
    }

    @Test
    void keepsClientOffsetWhenReading() {
        String json = """
                {"datetime": "2022-01-30T00:00:00+06:00"}
                """;

        Sample sample = mapper.readValue(json, Sample.class);

        assertThat(sample.datetime().getOffset()).isEqualTo(ZoneOffset.ofHours(6));
        assertThat(sample.datetime().toLocalDateTime()).hasToString("2022-01-30T00:00");
    }

    @Test
    void keepsOffsetWhenWriting() {
        OffsetDateTime datetime = OffsetDateTime.of(2022, 1, 30, 0, 0, 0, 0, ZoneOffset.ofHours(6));

        String json = mapper.writeValueAsString(new Sample(null, null, datetime));

        assertThat(json).contains("\"datetime\":\"2022-01-30T00:00:00+06:00\"");
    }

    @Test
    void writesDecimalsInPlainNotation() {
        String json = mapper.writeValueAsString(new Sample(null, new BigDecimal("1E+3").setScale(2), null));

        assertThat(json).contains("\"sum\":1000.00");
    }
}
