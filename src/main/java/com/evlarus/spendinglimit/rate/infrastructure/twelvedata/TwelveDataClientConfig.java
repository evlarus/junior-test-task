package com.evlarus.spendinglimit.rate.infrastructure.twelvedata;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.rates.provider", havingValue = "twelvedata")
@ImportHttpServices(group = TwelveDataClientConfig.GROUP, types = TwelveDataApi.class)
@EnableResilientMethods
class TwelveDataClientConfig {

    static final String GROUP = "twelvedata";

    /**
     * The application's own JSON contract is strict (unknown properties and type coercion are rejected).
     * A third-party response must not break when the provider adds a field, so this client gets its own
     * lenient mapper. Runs after Spring Boot's configurers, which would otherwise install the strict converter.
     */
    @Bean
    RestClientHttpServiceGroupConfigurer twelveDataLenientJson() {
        JsonMapper lenientMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        return new RestClientHttpServiceGroupConfigurer() {

            @Override
            public void configureGroups(HttpServiceGroupConfigurer.Groups<org.springframework.web.client.RestClient.Builder> groups) {
                groups.filterByName(GROUP).forEachClient((group, builder) ->
                        builder.messageConverters(List.of(new JacksonJsonHttpMessageConverter(lenientMapper))));
            }

            @Override
            public int getOrder() {
                return Ordered.LOWEST_PRECEDENCE;
            }
        };
    }
}
