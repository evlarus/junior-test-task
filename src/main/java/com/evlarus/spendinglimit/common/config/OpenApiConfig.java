package com.evlarus.spendinglimit.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

/** OpenAPI document served at {@code /v3/api-docs}, browsable in Swagger UI at {@code /swagger-ui.html}. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String PROBLEM_SCHEMA = "Problem";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Spending Limit Service")
                .version("v1")
                .description("""
                        Tracks expense transactions of client accounts against monthly limits in USD.
                        Integration API: bank systems report transactions. Client API: limits and transactions \
                        that exceeded them. Errors are RFC 9457 problem details.
                        """));
    }

    /**
     * springdoc describes schemas with its own Jackson 2 mapper, which does not know the application's
     * snake_case naming (configured for Jackson 3); without this the documentation would show {@code limitSum}
     * while the API expects {@code limit_sum}. A copy is used: the original mapper also writes the OpenAPI
     * document itself, whose own field names must stay camelCase.
     */
    @Bean
    OpenApiCustomizer problemDetailResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            openApi.getPaths().values().stream()
                    .flatMap(path -> path.readOperations().stream())
                    .forEach(OpenApiConfig::describeErrors);
        };
    }

    private static void describeErrors(Operation operation) {
        ApiResponses responses = operation.getResponses();
        responses.computeIfAbsent("500", code -> new ApiResponse().description("Unexpected error"));
        responses.forEach((code, response) -> {
            if (code.startsWith("4") || code.startsWith("5")) {
                response.setContent(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))));
            }
        });
    }

    private static Schema<?> problemSchema() {
        Schema<?> violation = new ObjectSchema()
                .addProperty("field", new StringSchema().example("account_from"))
                .addProperty("message", new StringSchema().example("must consist of exactly 10 digits"));
        return new ObjectSchema()
                .description("RFC 9457 problem details")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Bad Request"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("Request validation failed"))
                .addProperty("instance", new StringSchema().example("/api/client/v1/limits"))
                .addProperty("errors", new ArraySchema().items(violation)
                        .description("Violated constraints, only for validation errors"));
    }

    @Bean
    ModelResolver snakeCaseModelResolver(ObjectMapperProvider objectMapperProvider) {
        ObjectMapper schemaMapper = objectMapperProvider.jsonMapper().copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(schemaMapper);
    }
}
