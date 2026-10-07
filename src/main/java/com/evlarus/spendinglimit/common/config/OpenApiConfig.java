package com.evlarus.spendinglimit.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document served at {@code /v3/api-docs}, browsable in Swagger UI at {@code /swagger-ui.html}. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

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
    ModelResolver snakeCaseModelResolver(ObjectMapperProvider objectMapperProvider) {
        ObjectMapper schemaMapper = objectMapperProvider.jsonMapper().copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(schemaMapper);
    }
}
