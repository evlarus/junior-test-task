package com.evlarus.spendinglimit.rate.application;

import static com.evlarus.spendinglimit.support.DomainFixtures.KZT;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RatesPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void twelveDataRequiresAnApiKey() {
        Set<ConstraintViolation<RatesProperties>> violations = validator.validate(properties(RatesProperties.Provider.TWELVEDATA, " "));

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .singleElement().asString().contains("TWELVEDATA_API_KEY");
    }

    @Test
    void fixedRatesNeedNoApiKey() {
        assertThat(validator.validate(properties(RatesProperties.Provider.FIXED, null))).isEmpty();
    }

    @Test
    void twelveDataWithAKeyIsValid() {
        assertThat(validator.validate(properties(RatesProperties.Provider.TWELVEDATA, "key"))).isEmpty();
    }

    private static RatesProperties properties(RatesProperties.Provider provider, String apiKey) {
        return new RatesProperties(provider, 10, Set.of(KZT),
                new RatesProperties.TwelveData(apiKey), new RatesProperties.Fixed(Map.of()));
    }
}
