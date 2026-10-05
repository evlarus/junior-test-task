package com.evlarus.spendinglimit;

import com.evlarus.spendinglimit.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The whole application starts against a real PostgreSQL: datasource, Flyway, Hibernate schema validation,
 * Actuator, springdoc and Logbook are all compatible with each other and with the configuration.
 */
@IntegrationTest
class ApplicationSmokeIT {

    @Autowired
    private RestTestClient client;

    @Test
    void healthIsUp() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void openApiDocumentIsPublished() {
        client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().jsonPath("$.openapi").exists();
    }

    @Test
    void swaggerUiIsServed() {
        client.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void unknownEndpointReturnsProblemDetail() {
        client.get().uri("/no-such-endpoint")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
