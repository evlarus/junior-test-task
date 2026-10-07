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
    void openApiDocumentUsesTheSameJsonNamesAsTheApi() {
        client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.schemas.SetLimitRequest.properties.limit_sum").exists()
                .jsonPath("$.components.schemas.SetLimitRequest.properties.limitSum").doesNotExist()
                .jsonPath("$.components.schemas.SetLimitRequest.properties.expense_category.enum[0]").isEqualTo("product")
                .jsonPath("$.paths['/api/client/v1/limits'].post.operationId").exists();
    }

    @Test
    void swaggerUiIsServed() {
        client.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void openApiDocumentDescribesErrorsAsProblemDetails() {
        client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.schemas.Problem.properties.errors").exists()
                .jsonPath("$.paths['/api/client/v1/limits'].post.responses['400'].content['application/problem+json'].schema['$ref']")
                .isEqualTo("#/components/schemas/Problem")
                .jsonPath("$.paths['/api/integration/v1/transactions'].post.responses['500'].content['application/problem+json']")
                .exists()
                .jsonPath("$.paths['/api/integration/v1/transactions'].post.responses['201'].content['application/problem+json']")
                .doesNotExist();
    }

    @Test
    void unknownEndpointReturnsProblemDetail() {
        client.get().uri("/no-such-endpoint")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
