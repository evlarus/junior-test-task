package com.evlarus.spendinglimit.transaction.api;

import com.evlarus.spendinglimit.common.api.ApiMappings;
import com.evlarus.spendinglimit.common.api.ExpenseCategoryDto;
import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.ExpenseCategory;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.transaction.application.ExceededTransaction;
import com.evlarus.spendinglimit.transaction.application.ReceiveTransaction;
import com.evlarus.spendinglimit.transaction.domain.ProcessingResult;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = ApiMappings.class)
public interface TransactionApiMapper {

    ExpenseCategory toDomain(ExpenseCategoryDto category);

    @Mapping(target = "currencyShortname", source = "amount.currency")
    @Mapping(target = "sum", source = "amount.amount")
    @Mapping(target = "expenseCategory", source = "category")
    @Mapping(target = "datetime", source = "occurredAt")
    @Mapping(target = "limitSum", source = "limitAmount.amount")
    @Mapping(target = "limitDatetime", source = "limitSetAt")
    @Mapping(target = "limitCurrencyShortname", source = "limitAmount.currency")
    ExceededTransactionResponse toResponse(ExceededTransaction transaction);

    default ReceiveTransaction toCommand(ReceiveTransactionRequest request) {
        return new ReceiveTransaction(
                AccountNumber.of(request.accountFrom()),
                AccountNumber.of(request.accountTo()),
                new Money(request.sum(), Currency.getInstance(request.currencyShortname())),
                toDomain(request.expenseCategory()),
                request.datetime());
    }

    default TransactionReceiptResponse toReceipt(Transaction transaction) {
        ProcessingResult result = transaction.result().orElse(null);
        return new TransactionReceiptResponse(
                Objects.requireNonNull(transaction.id(), "transaction id"),
                transaction.status().name().toLowerCase(Locale.ROOT),
                result == null ? null : result.amountUsd().amount(),
                result == null ? null : result.limitExceeded());
    }
}
