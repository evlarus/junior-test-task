package com.evlarus.spendinglimit.transaction.api;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.transaction.application.ExceededTransactionsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/v1/transactions")
@Tag(name = "Transactions", description = "Transactions of a client account")
public class ClientTransactionController {

    private final ExceededTransactionsQuery exceededTransactions;
    private final TransactionApiMapper mapper;

    public ClientTransactionController(ExceededTransactionsQuery exceededTransactions, TransactionApiMapper mapper) {
        this.exceededTransactions = exceededTransactions;
        this.mapper = mapper;
    }

    @GetMapping("/limit-exceeded")
    @Operation(
            summary = "Transactions that exceeded the limit",
            description = "Oldest first, each with the limit it exceeded: the limit in force when it was made")
    @ApiResponse(responseCode = "200", description = "Transactions, empty when none exceeded a limit")
    @ApiResponse(responseCode = "400", description = "Invalid account number or paging")
    public List<ExceededTransactionResponse> limitExceeded(
            @Parameter(description = "Client account, 10 digits", example = "0000000123")
            @RequestParam
            @Pattern(regexp = "\\d{10}", message = "must consist of exactly 10 digits")
            String account,
            @Parameter(description = "Page number, from 0")
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "100")
            @Min(1)
            @Max(1000)
            int size) {
        return exceededTransactions.findByAccount(AccountNumber.of(account), page, size).stream()
                .map(mapper::toResponse)
                .toList();
    }
}
