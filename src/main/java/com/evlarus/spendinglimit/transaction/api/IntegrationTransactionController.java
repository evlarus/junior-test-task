package com.evlarus.spendinglimit.transaction.api;

import com.evlarus.spendinglimit.transaction.application.TransactionIntake;
import com.evlarus.spendinglimit.transaction.domain.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration/v1/transactions")
@Tag(name = "Integration: transactions", description = "Expense transactions reported by bank systems in real time")
public class IntegrationTransactionController {

    private final TransactionIntake intake;
    private final TransactionApiMapper mapper;

    public IntegrationTransactionController(TransactionIntake intake, TransactionApiMapper mapper) {
        this.intake = intake;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(
            summary = "Receive an expense transaction",
            description = "The transaction is stored first and never lost. It is converted to USD and checked against"
                    + " the monthly limit right away when the exchange rate is available, otherwise it stays pending"
                    + " and is processed in the background.")
    @ApiResponse(responseCode = "201", description = "Stored and processed")
    @ApiResponse(responseCode = "202", description = "Stored, processing deferred until the exchange rate is available")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "422", description = "Unsupported currency or a transaction dated in the future")
    public ResponseEntity<TransactionReceiptResponse> receive(@Valid @RequestBody ReceiveTransactionRequest request) {
        Transaction transaction = intake.receive(mapper.toCommand(request));
        HttpStatus status = transaction.isPending() ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(mapper.toReceipt(transaction));
    }
}
