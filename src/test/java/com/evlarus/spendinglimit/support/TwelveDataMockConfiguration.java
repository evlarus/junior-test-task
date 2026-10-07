package com.evlarus.spendinglimit.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * WireMock in place of Twelve Data for all integration tests. One server lives as long as the shared Spring
 * context; tests reset it before use and stub what they need with {@link TwelveDataStubs}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TwelveDataMockConfiguration {

    @Bean(destroyMethod = "stop")
    WireMockServer twelveDataMock() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        return server;
    }

    @Bean
    DynamicPropertyRegistrar twelveDataMockUrl(WireMockServer twelveDataMock) {
        return registry -> registry.add("spring.http.serviceclient.twelvedata.base-url", twelveDataMock::baseUrl);
    }
}
