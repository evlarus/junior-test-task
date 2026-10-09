---
name: add-exchange-rate-provider
description: Add a new source of exchange rates (another FX API such as Open Exchange Rates, Alpha Vantage, a central bank feed) to this spending-limit service, or replace Twelve Data. Use when the task mentions a new rate provider, a new FX API, switching app.rates.provider, or rates for a new currency that the current provider does not offer.
---

# Add an exchange rate provider

The service converts every transaction to USD with a daily closing rate. Rates come from one
`ExchangeRateProvider`, chosen by `app.rates.provider`. `ExchangeRateService` already stores every
fetched day in the database, falls back to the previous close on weekends and holidays, and merges
concurrent requests, so a provider only has to return daily closes.

## 1. Implement the port

Create `src/main/java/com/evlarus/spendinglimit/rate/infrastructure/<provider>/<Provider>RateProvider.java`
implementing `com.evlarus.spendinglimit.rate.application.ExchangeRateProvider`:

- `dailyCloses(currency, from, to)` returns `List<DailyClose>` for trading days between `from` and `to`
  **inclusive**; days without trading are simply absent.
- `DailyClose.unitsPerUsd` is **units of the currency per 1 USD** (about 450 for KZT). If the API quotes
  USD per unit, invert it with enough precision (`BigDecimal.ONE.divide(rate, 16, RoundingMode.HALF_EVEN)`);
  `DailyClose` rounds to 8 decimals itself.
- Keep one close per day: real APIs return duplicates (see the `LinkedHashMap` in `TwelveDataRateProvider`).
- Errors:
  - `TransientRateProviderException` for HTTP 5xx, 429, refused connection — retried immediately;
  - `RateProviderException` for everything else, **including timeouts** — not retried, the transaction
    stays `PENDING` and `PendingTransactionRetrier` tries again later.
- Annotate the class with `@Component` and
  `@ConditionalOnProperty(name = "app.rates.provider", havingValue = "<provider>")`.
- Add `@Retryable(includes = TransientRateProviderException.class, ...)` like `TwelveDataRateProvider`.

## 2. HTTP client

Follow `rate/infrastructure/twelvedata/TwelveDataClientConfig.java`:

- a package-private `@HttpExchange` interface with `String` date parameters (ISO format);
- `@ImportHttpServices(group = "<provider>", types = ...)` on a configuration class with the same
  `@ConditionalOnProperty` and `@EnableResilientMethods`;
- a `RestClientHttpServiceGroupConfigurer` with `getOrder() = Ordered.LOWEST_PRECEDENCE` that installs a
  **lenient** `JsonMapper` (the application's own mapper rejects unknown fields) and the
  `LogbookClientHttpRequestInterceptor`;
- connection settings in `src/main/resources/application.yml` under
  `spring.http.serviceclient.<provider>`: `base-url`, `connect-timeout`, `read-timeout`, and the API key in
  `default-header` — never as a query parameter. If the key must be a query parameter, add its name to
  `logbook.obfuscate.parameters`.

## 3. Configuration

- Add the constant to `RatesProperties.Provider` (`rate/application/RatesProperties.java`) and, if the
  provider needs a key, a property record plus an `@AssertTrue` check like `isApiKeyPresentWhenRequired`.
- Add defaults to `application.yml`, the variables to `.env.example` and to the `app` service of
  `docker-compose.yml`, and a line to the configuration table in `README.md`.
- Every currency in `app.rates.supported-currencies` must be available from the new provider.

## 4. Tests

- Integration test `src/test/java/com/evlarus/spendinglimit/rate/infrastructure/<provider>/<Provider>RateProviderIT.java`
  annotated with `@IntegrationTest`, modelled on `TwelveDataRateProviderIT`: success, error in the body,
  5xx retried, 429 retried, timeout not retried, malformed response, duplicate day.
- WireMock: copy `support/TwelveDataMockConfiguration` (a `WireMockServer` bean plus a
  `DynamicPropertyRegistrar` for `spring.http.serviceclient.<provider>.base-url`) and import it in
  `support/IntegrationTest`. Keep one shared Spring context: no `@MockitoBean`, no per-test properties.
- The integration tests run with `app.rates.provider: twelvedata` (`src/test/resources/application-test.yml`);
  to test the new provider through `ExchangeRateService` as well, switch the property there and keep
  `TwelveDataRateProviderIT` working by testing the old adapter directly.
- Unit tests for pure parsing logic, like `rate/infrastructure/fixed/FixedRateProviderTest`.

## 5. Check

```bash
./mvnw verify
```

Docker must be running (PostgreSQL in Testcontainers). The build fails on ArchUnit violations
(`ArchitectureTest`), domain coverage below 90% and unmapped MapStruct fields.

Write no comments in new code; express intent through names.
