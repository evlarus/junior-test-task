package com.evlarus.spendinglimit.limit.api;

import com.evlarus.spendinglimit.common.api.ApiMappings;
import com.evlarus.spendinglimit.common.api.ExpenseCategoryDto;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.limit.application.LimitUsage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Generated at compile time; a target field without a source fails the build (unmappedTargetPolicy=ERROR). */
@Mapper(uses = ApiMappings.class)
public interface LimitApiMapper {

    ExpenseCategory toDomain(ExpenseCategoryDto category);

    @Mapping(target = "id", source = "limit.id")
    @Mapping(target = "account", source = "limit.account")
    @Mapping(target = "expenseCategory", source = "limit.category")
    @Mapping(target = "limitSum", source = "limit.amount.amount")
    @Mapping(target = "limitCurrencyShortname", source = "limit.amount.currency")
    @Mapping(target = "limitDatetime", source = "limit.setAt")
    @Mapping(target = "systemDefault", source = "limit.systemDefault")
    @Mapping(target = "checkedTransactions", source = "transactions")
    @Mapping(target = "checkedSumUsd", source = "spentUsd.amount")
    LimitResponse toResponse(LimitUsage usage);
}
