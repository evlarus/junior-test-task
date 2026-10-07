package com.evlarus.spendinglimit.e2e;

import static com.evlarus.spendinglimit.support.TwelveDataStubs.stubCloses;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.evlarus.spendinglimit.support.MutableClock;
import com.evlarus.spendinglimit.support.TestAccounts;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
class SpecificationScenariosE2EIT {

    private static final String LIMITS = "/api/client/v1/limits";
    private static final String TRANSACTIONS = "/api/integration/v1/transactions";
    private static final String EXCEEDED = "/api/client/v1/transactions/limit-exceeded";
    private static final BigDecimal KZT_PER_USD = new BigDecimal("450.00");

    @Autowired
    private RestTestClient client;

    @Autowired
    private MutableClock clock;

    @Autowired
    private WireMockServer twelveDataMock;

    private final AccountNumber account = TestAccounts.unique();

    @BeforeEach
    void quoteTengeAt450PerDollarOnTradingDays() {
        twelveDataMock.resetAll();
        stubCloses(twelveDataMock, "KZT", LocalDate.of(2021, 12, 1).datesUntil(LocalDate.of(2022, 3, 1))
                .filter(day -> day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY)
                .collect(Collectors.toMap(Function.identity(), day -> KZT_PER_USD.toPlainString())));
    }

    @AfterEach
    void restoreTheClock() {
        clock.reset();
    }

    @Test
    void case1_newLimitDoesNotChangeTheFlagsOfEarlierTransactions() {
        setLimitAt("2022-01-01T00:00:00Z", "1000");
        spendUsdAt("2022-01-02T12:00:00+06:00", "500", false);
        spendUsdAt("2022-01-03T12:00:00+06:00", "600", true);
        setLimitAt("2022-01-10T00:00:00Z", "2000");
        spendUsdAt("2022-01-11T12:00:00+06:00", "100", false);
        spendUsdAt("2022-01-12T12:00:00+06:00", "700", false);
        spendUsdAt("2022-01-13T12:00:00+06:00", "100", false);
        spendUsdAt("2022-01-13T13:00:00+06:00", "100", true);

        client.get().uri(EXCEEDED + "?account={account}", account.value())
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        [{"account_from": "%1$s", "account_to": "9999999999", "currency_shortname": "KZT",
                          "sum": 270000.00, "expense_category": "product", "datetime": "2022-01-03T12:00:00+06:00",
                          "limit_sum": 1000.00, "limit_datetime": "2022-01-01T00:00:00Z", "limit_currency_shortname": "USD"},
                         {"account_from": "%1$s", "account_to": "9999999999", "currency_shortname": "KZT",
                          "sum": 45000.00, "expense_category": "product", "datetime": "2022-01-13T13:00:00+06:00",
                          "limit_sum": 2000.00, "limit_datetime": "2022-01-10T00:00:00Z", "limit_currency_shortname": "USD"}]
                        """.formatted(account.value()), JsonCompareMode.STRICT);
    }

    @Test
    void case2_lowerLimitMakesTheFollowingTransactionsExceedIt() {
        setLimitAt("2022-02-01T00:00:00Z", "1000");
        spendUsdAt("2022-02-02T12:00:00+06:00", "500", false);
        spendUsdAt("2022-02-03T12:00:00+06:00", "100", false);
        setLimitAt("2022-02-10T00:00:00Z", "400");
        spendUsdAt("2022-02-11T12:00:00+06:00", "100", true);
        spendUsdAt("2022-02-12T12:00:00+06:00", "100", true);

        client.get().uri(EXCEEDED + "?account={account}", account.value())
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        [{"account_from": "%1$s", "account_to": "9999999999", "currency_shortname": "KZT",
                          "sum": 45000.00, "expense_category": "product", "datetime": "2022-02-11T12:00:00+06:00",
                          "limit_sum": 400.00, "limit_datetime": "2022-02-10T00:00:00Z", "limit_currency_shortname": "USD"},
                         {"account_from": "%1$s", "account_to": "9999999999", "currency_shortname": "KZT",
                          "sum": 45000.00, "expense_category": "product", "datetime": "2022-02-12T12:00:00+06:00",
                          "limit_sum": 400.00, "limit_datetime": "2022-02-10T00:00:00Z", "limit_currency_shortname": "USD"}]
                        """.formatted(account.value()), JsonCompareMode.STRICT);
    }

    private void setLimitAt(String moment, String usd) {
        clock.setTo(moment);
        client.post().uri(LIMITS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("account", account.value(), "expense_category", "product", "limit_sum", new BigDecimal(usd)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.limit_datetime").isEqualTo(moment);
    }

    private void spendUsdAt(String datetime, String usd, boolean expectedLimitExceeded) {
        clock.setTo(OffsetDateTime.parse(datetime).toInstant().plusSeconds(60));
        client.post().uri(TRANSACTIONS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "account_from", account.value(),
                        "account_to", "9999999999",
                        "currency_shortname", "KZT",
                        "sum", new BigDecimal(usd).multiply(KZT_PER_USD),
                        "expense_category", "product",
                        "datetime", datetime))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sum_usd").isEqualTo(new BigDecimal(usd).doubleValue())
                .jsonPath("$.limit_exceeded").isEqualTo(expectedLimitExceeded);
    }
}
