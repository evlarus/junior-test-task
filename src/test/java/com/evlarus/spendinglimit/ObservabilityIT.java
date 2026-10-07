package com.evlarus.spendinglimit;

import static com.evlarus.spendinglimit.support.TwelveDataStubs.stubCloses;
import static org.assertj.core.api.Assertions.assertThat;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.support.IntegrationTest;
import com.evlarus.spendinglimit.support.TestAccounts;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIT {

    @Autowired
    private RestTestClient client;

    @Autowired
    private WireMockServer twelveDataMock;

    private final AccountNumber account = TestAccounts.unique();

    @BeforeEach
    void quoteTenge() {
        twelveDataMock.resetAll();
        stubCloses(twelveDataMock, "KZT", Map.of(LocalDate.of(2023, 7, 3), "450.00"));
    }

    @Test
    void livenessAndReadinessProbesAreUp() {
        client.get().uri("/actuator/health/liveness").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
        client.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void prometheusExposesBusinessAndExternalApiMetrics() {
        receiveTengeTransaction();

        String metrics = client.get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();

        assertThat(metrics)
                .contains("transactions_processed_total{")
                .contains("transactions_pending{")
                .contains("exchange_rate_lookups_total{")
                .containsPattern("http_client_requests_seconds_count\\{[^}]*uri=\"/time_series[?\"]");
    }

    @Test
    void httpLogsHideAccountNumbersAndTheApiKey(CapturedOutput output) {
        receiveTengeTransaction();
        client.get().uri("/api/client/v1/limits?account={account}", account.value()).exchange().expectStatus().isOk();
        client.get().uri("/api/client/v1/transactions/limit-exceeded?account={account}", account.value())
                .exchange().expectStatus().isOk();

        assertThat(output.getOut())
                .contains("/api/integration/v1/transactions")
                .contains("/api/client/v1/limits")
                .contains("/api/client/v1/transactions/limit-exceeded")
                .contains("/time_series")
                .doesNotContain(account.value())
                .doesNotContain("test-api-key");
    }

    private void receiveTengeTransaction() {
        client.post().uri("/api/integration/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"account_from": "%s", "account_to": "9999999999", "currency_shortname": "KZT",
                         "sum": 4500.00, "expense_category": "service", "datetime": "2023-07-03T12:00:00+06:00"}
                        """.formatted(account.value()))
                .exchange()
                .expectStatus().isCreated();
    }
}
