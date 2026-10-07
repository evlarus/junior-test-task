package com.evlarus.spendinglimit.limit.api;

import static com.evlarus.spendinglimit.support.DomainFixtures.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evlarus.spendinglimit.common.api.ApiMappings;
import com.evlarus.spendinglimit.common.domain.BusinessCalendar;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.application.LimitService;
import com.evlarus.spendinglimit.limit.application.LimitUsage;
import com.evlarus.spendinglimit.limit.domain.LimitAlreadySetException;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

@WebMvcTest(ClientLimitController.class)
@Import({LimitApiMapperImpl.class, ApiMappings.class, ClientLimitControllerTest.UtcCalendar.class})
class ClientLimitControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class UtcCalendar {

        @Bean
        BusinessCalendar businessCalendar() {
            return new BusinessCalendar(ZoneOffset.UTC);
        }
    }

    private static final String LIMITS = "/api/client/v1/limits";
    private static final SpendingLimit SAVED = new SpendingLimit(
            5L, CLIENT, ExpenseCategory.PRODUCT, Money.usd("2000"), Instant.parse("2022-01-10T00:00:00Z"), false);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private LimitService service;

    @Test
    void setsALimitThatTakesEffectNow() {
        when(service.setLimit(CLIENT, ExpenseCategory.PRODUCT, Money.usd("2000.00"))).thenReturn(SAVED);

        MvcTestResult result = post("""
                {"account": "0000000123", "expense_category": "product", "limit_sum": 2000.00}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"id": 5, "account": "0000000123", "expense_category": "product", "limit_sum": 2000.00,
                 "limit_datetime": "2022-01-10T00:00:00Z", "limit_currency_shortname": "USD",
                 "system_default": false, "checked_transactions": 0, "exceeded_transactions": 0,
                 "checked_sum_usd": 0.00}
                """);
    }

    @Test
    void acceptsAnExplicitUsdCurrency() {
        when(service.setLimit(any(), any(), any())).thenReturn(SAVED);

        assertThat(post("""
                {"account": "0000000123", "expense_category": "service", "limit_sum": 0, "limit_currency_shortname": "USD"}
                """)).hasStatus(HttpStatus.CREATED);
        verify(service).setLimit(CLIENT, ExpenseCategory.SERVICE, Money.usd("0"));
    }

    @Test
    void dateOfTheLimitCannotBePassed() {
        MvcTestResult result = post("""
                {"account": "0000000123", "expense_category": "product", "limit_sum": 1000,
                 "limit_datetime": "2021-01-01T00:00:00Z"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Unknown property 'limit_datetime'");
        verify(service, never()).setLimit(any(), any(), any());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "account of 9 digits    | {\"account\": \"000000123\", \"expense_category\": \"product\", \"limit_sum\": 1}  | account",
        "negative sum           | {\"account\": \"0000000123\", \"expense_category\": \"product\", \"limit_sum\": -1} | limit_sum",
        "three decimals         | {\"account\": \"0000000123\", \"expense_category\": \"product\", \"limit_sum\": 1.001} | limit_sum",
        "missing category       | {\"account\": \"0000000123\", \"limit_sum\": 1}                                    | expense_category",
        "currency other than USD | {\"account\": \"0000000123\", \"expense_category\": \"product\", \"limit_sum\": 1, \"limit_currency_shortname\": \"EUR\"} | limit_currency_shortname"
    })
    void invalidRequestNamesTheField(String description, String body, String field) {
        MvcTestResult result = post(body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly(field);
    }

    @Test
    void unknownCategoryIsRejected() {
        MvcTestResult result = post("""
                {"account": "0000000123", "expense_category": "food", "limit_sum": 1}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Invalid value for property 'expense_category'");
    }

    @Test
    void limitSetAtTheSameMomentIsAConflict() {
        when(service.setLimit(any(), any(), any())).thenThrow(new LimitAlreadySetException(ExpenseCategory.PRODUCT));

        MvcTestResult result = post("""
                {"account": "0000000123", "expense_category": "product", "limit_sum": 1}
                """);

        assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void listsLimitsWithUsage() {
        SpendingLimit systemDefault = new SpendingLimit(
                6L, CLIENT, ExpenseCategory.SERVICE, Money.usd("1000"), Instant.parse("2022-01-02T08:00:00Z"), true);
        when(service.getLimits(CLIENT)).thenReturn(List.of(
                new LimitUsage(SAVED, 4, 1, Money.usd("1300")),
                LimitUsage.unused(systemDefault)));

        MvcTestResult result = mvc.get().uri(LIMITS).param("account", "0000000123").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                [{"id": 5, "expense_category": "product", "system_default": false,
                  "checked_transactions": 4, "exceeded_transactions": 1, "checked_sum_usd": 1300.00},
                 {"id": 6, "expense_category": "service", "system_default": true,
                  "limit_datetime": "2022-01-02T08:00:00Z", "checked_transactions": 0}]
                """);
    }

    @Test
    void listingRequiresAValidAccount() {
        MvcTestResult result = mvc.get().uri(LIMITS).param("account", "12345").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("account");
    }

    private MvcTestResult post(String body) {
        return mvc.post().uri(LIMITS).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }
}
