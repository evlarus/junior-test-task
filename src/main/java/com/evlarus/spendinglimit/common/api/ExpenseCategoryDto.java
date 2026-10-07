package com.evlarus.spendinglimit.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Expense category as it appears in JSON ({@code product}, {@code service}). Kept apart from the domain enum,
 * which knows nothing about JSON; MapStruct maps the two by constant name.
 */
@Schema(description = "Expense category")
public enum ExpenseCategoryDto {

    @JsonProperty("product")
    PRODUCT,

    @JsonProperty("service")
    SERVICE
}
