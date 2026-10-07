package com.evlarus.spendinglimit.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application on a random HTTP port with PostgreSQL in Testcontainers and WireMock in place of Twelve Data.
 *
 * <p>Every integration test uses exactly this annotation and nothing that changes the context
 * (no {@code @MockitoBean}, no extra properties), so Spring caches one context and one database
 * container for the whole test run. Tests isolate their data by using unique account numbers.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TwelveDataMockConfiguration.class})
public @interface IntegrationTest {
}
