package com.evlarus.spendinglimit.common.config;

import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * String properties accept only JSON strings: {@code "account_from": 123} is rejected instead of
 * silently becoming {@code "123"} (an account number sent as a number has already lost its leading zeros).
 *
 * <p>{@code MapperFeature.ALLOW_COERCION_OF_SCALARS=false} does not cover number-to-string coercion in
 * Jackson 3. A module is used rather than a mapper customizer because Spring Boot registers {@code JacksonModule}
 * beans in every context, including {@code @JsonTest} and {@code @WebMvcTest} slices, so tests see the same
 * JSON rules as production.
 */
@Component
public final class StrictStringModule extends SimpleModule {

    public StrictStringModule() {
        super("strict-string");
        addDeserializer(String.class, new StrictStringDeserializer());
    }

    private static final class StrictStringDeserializer extends StdScalarDeserializer<String> {

        StrictStringDeserializer() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                return parser.getString();
            }
            return (String) context.handleUnexpectedToken(String.class, parser);
        }
    }
}
