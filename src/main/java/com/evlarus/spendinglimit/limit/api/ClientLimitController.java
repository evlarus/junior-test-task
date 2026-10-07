package com.evlarus.spendinglimit.limit.api;

import com.evlarus.spendinglimit.common.domain.AccountNumber;
import com.evlarus.spendinglimit.common.domain.Money;
import com.evlarus.spendinglimit.limit.application.LimitService;
import com.evlarus.spendinglimit.limit.application.LimitUsage;
import com.evlarus.spendinglimit.limit.domain.SpendingLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/v1/limits")
@Tag(name = "Limits", description = "Monthly spending limits in USD per expense category")
public class ClientLimitController {

    private final LimitService service;
    private final LimitApiMapper mapper;

    public ClientLimitController(LimitService service, LimitApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Set a new limit",
            description = "The limit takes effect now; its date is set by the service and cannot be passed."
                    + " Existing limits are never changed: transactions made before keep the limit they were checked"
                    + " against.")
    @ApiResponse(responseCode = "201", description = "Limit set")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "Another limit took effect at the same moment")
    public LimitResponse setLimit(@Valid @RequestBody SetLimitRequest request) {
        SpendingLimit limit = service.setLimit(
                AccountNumber.of(request.account()),
                mapper.toDomain(request.expenseCategory()),
                new Money(request.limitSum(), Money.USD));
        return mapper.toResponse(LimitUsage.unused(limit));
    }

    @GetMapping
    @Operation(summary = "All limits of an account", description = "By category, newest first, with usage statistics")
    @ApiResponse(responseCode = "200", description = "Limits, empty when none has been set or applied yet")
    @ApiResponse(responseCode = "400", description = "Invalid account number")
    public List<LimitResponse> getLimits(
            @Parameter(description = "Client account, 10 digits", example = "0000000123")
            @RequestParam
            @Pattern(regexp = "\\d{10}", message = "must consist of exactly 10 digits")
            String account) {
        return service.getLimits(AccountNumber.of(account)).stream().map(mapper::toResponse).toList();
    }
}
