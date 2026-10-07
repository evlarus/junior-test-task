package com.evlarus.spendinglimit.transaction.api;

import static com.evlarus.spendinglimit.support.DomainFixtures.CLIENT;
import static com.evlarus.spendinglimit.support.DomainFixtures.COUNTERPARTY;
import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evlarus.spendinglimit.common.api.ApiMappings;
import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.transaction.application.ReceiveTransaction;
import com.evlarus.spendinglimit.transaction.application.TransactionIntake;
import com.evlarus.spendinglimit.transaction.application.UnsupportedCurrencyException;
import com.evlarus.spendinglimit.transaction.domain.ProcessingResult;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import com.evlarus.spendinglimit.transaction.domain.TransactionInFutureException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(IntegrationTransactionController.class)
@Import({TransactionApiMapperImpl.class, ApiMappings.class, IntegrationTransactionControllerTest.UtcCalendar.class})
class IntegrationTransactionControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class UtcCalendar {

        @Bean
        BusinessCalendar businessCalendar() {
            return new BusinessCalendar(ZoneOffset.UTC);
        }
    }

    private static final String TRANSACTIONS = "/api/integration/v1/transactions";
    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2022-01-30T00:00:00+06:00");
    private static final Instant NOW = Instant.parse("2022-01-30T00:00:01Z");
    private static final String VALID = """
            {"account_from": "0000000123", "account_to": "9999999999", "currency_shortname": "KZT",
             "sum": 10000.45, "expense_category": "product", "datetime": "2022-01-30T00:00:00+06:00"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TransactionIntake intake;

    @Test
    void processedTransactionIsCreated() {
        when(intake.receive(any())).thenReturn(transaction(new ProcessingResult(Money.usd("22.22"), 3, true, NOW)));

        MvcTestResult result = post(VALID);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"id": 11, "status": "processed", "sum_usd": 22.22, "limit_exceeded": true}
                """);
        verify(intake).receive(new ReceiveTransaction(
                CLIENT, COUNTERPARTY, Money.of("10000.45", KZT), ExpenseCategory.PRODUCT, OCCURRED_AT));
    }

    @Test
    void pendingTransactionIsAccepted() {
        when(intake.receive(any())).thenReturn(transaction(null));

        MvcTestResult result = post(VALID);

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"id": 11, "status": "pending", "sum_usd": null, "limit_exceeded": null}
                """);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "short account          | account_from       | \"0000000123\" | \"123\"",
        "unknown currency code  | currency_shortname | \"KZT\"        | \"ABC\"",
        "lowercase currency     | currency_shortname | \"KZT\"        | \"kzt\"",
        "zero sum               | sum                | 10000.45       | 0",
        "three decimals         | sum                | 10000.45       | 1.005",
        "missing datetime       | datetime           | \"2022-01-30T00:00:00+06:00\" | null"
    })
    void invalidRequestNamesTheField(String description, String field, String valid, String invalid) {
        MvcTestResult result = post(VALID.replace("\"" + field + "\": " + valid, "\"" + field + "\": " + invalid));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly(field);
        verify(intake, never()).receive(any());
    }

    @Test
    void datetimeWithoutOffsetIsRejected() {
        MvcTestResult result = post(VALID.replace("2022-01-30T00:00:00+06:00", "2022-01-30T00:00:00"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Invalid value for property 'datetime'");
    }

    @Test
    void unsupportedCurrencyIsUnprocessable() {
        when(intake.receive(any())).thenThrow(new UnsupportedCurrencyException(Currency.getInstance("EUR")));

        assertThat(post(VALID)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void transactionFromTheFutureIsUnprocessable() {
        when(intake.receive(any())).thenThrow(new TransactionInFutureException(OCCURRED_AT));

        assertThat(post(VALID)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    private static Transaction transaction(ProcessingResult result) {
        return new Transaction(11L, CLIENT, COUNTERPARTY, Money.of("10000.45", KZT), ExpenseCategory.PRODUCT,
                OCCURRED_AT, NOW, result);
    }

    private MvcTestResult post(String body) {
        return mvc.post().uri(TRANSACTIONS).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }
}
