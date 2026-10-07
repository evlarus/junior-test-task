package com.evlarus.spendinglimit.transaction.api;

import static com.evlarus.spendinglimit.support.DomainFixtures.CLIENT;
import static com.evlarus.spendinglimit.support.DomainFixtures.COUNTERPARTY;
import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.evlarus.spendinglimit.common.api.ApiMappings;
import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.transaction.application.ExceededTransaction;
import com.evlarus.spendinglimit.transaction.application.ExceededTransactionsQuery;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(ClientTransactionController.class)
@Import({TransactionApiMapperImpl.class, ApiMappings.class, ClientTransactionControllerTest.UtcCalendar.class})
class ClientTransactionControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class UtcCalendar {

        @Bean
        BusinessCalendar businessCalendar() {
            return new BusinessCalendar(ZoneOffset.UTC);
        }
    }

    private static final String EXCEEDED = "/api/client/v1/transactions/limit-exceeded";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ExceededTransactionsQuery query;

    @Test
    void returnsTheExceededTransactionsWithTheirLimitInTheSpecifiedFormat() {
        when(query.findByAccount(CLIENT, 0, 100)).thenReturn(List.of(new ExceededTransaction(
                CLIENT, COUNTERPARTY, Money.of("270000", KZT), ExpenseCategory.PRODUCT,
                OffsetDateTime.parse("2022-01-03T12:00:00+06:00"),
                Money.usd("1000"), Instant.parse("2022-01-01T00:00:00Z"))));

        MvcTestResult result = mvc.get().uri(EXCEEDED).param("account", "0000000123").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                [{"account_from": "0000000123", "account_to": "9999999999", "currency_shortname": "KZT",
                  "sum": 270000.00, "expense_category": "product", "datetime": "2022-01-03T12:00:00+06:00",
                  "limit_sum": 1000.00, "limit_datetime": "2022-01-01T00:00:00Z", "limit_currency_shortname": "USD"}]
                """);
    }

    @Test
    void passesPaging() {
        when(query.findByAccount(CLIENT, 2, 10)).thenReturn(List.of());

        MvcTestResult result = mvc.get().uri(EXCEEDED)
                .param("account", "0000000123").param("page", "2").param("size", "10").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("[]");
    }

    @Test
    void rejectsInvalidAccountAndPaging() {
        assertThat(mvc.get().uri(EXCEEDED).param("account", "123").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri(EXCEEDED).param("account", "0000000123").param("size", "5000").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri(EXCEEDED).param("account", "0000000123").param("page", "-1").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
