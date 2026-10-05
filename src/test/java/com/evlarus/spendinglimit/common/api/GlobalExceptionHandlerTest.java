package com.evlarus.spendinglimit.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @RestController
    @RequestMapping("/test")
    static class TestController {

        record Payload(
                @NotNull @Pattern(regexp = "\\d{10}", message = "must consist of 10 digits") String accountFrom,
                @NotNull @Positive BigDecimal sum) {
        }

        @PostMapping("/payload")
        void accept(@Valid @RequestBody Payload payload) {
        }

        @GetMapping("/param")
        void param(@RequestParam @Pattern(regexp = "\\d{10}", message = "must consist of 10 digits") String account) {
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException("internal detail that must not leak");
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Test
    void invalidBodyReturnsFieldErrorsWithJsonNames() {
        MvcTestResult result = post("""
                {"account_from": "123", "sum": -1}
                """);

        assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field")
                .asArray().containsExactly("account_from", "sum");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message")
                .isEqualTo("must consist of 10 digits");
    }

    @Test
    void unknownPropertyIsNamedInDetail() {
        MvcTestResult result = post("""
                {"account_from": "0000000123", "sum": 1, "limit_datetime": "2022-01-01T00:00:00Z"}
                """);

        assertProblem(result, HttpStatus.BAD_REQUEST, "Unknown property 'limit_datetime'");
    }

    @Test
    void wrongValueTypeIsNamedInDetail() {
        MvcTestResult result = post("""
                {"account_from": "0000000123", "sum": "ten"}
                """);

        assertProblem(result, HttpStatus.BAD_REQUEST, "Invalid value for property 'sum'");
    }

    @Test
    void malformedJsonIsReported() {
        MvcTestResult result = post("{\"account_from\": ");

        assertProblem(result, HttpStatus.BAD_REQUEST, "Malformed JSON request body");
    }

    @Test
    void invalidRequestParameterReturnsFieldErrors() {
        MvcTestResult result = mvc.get().uri("/test/param").param("account", "abc").exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("account");
    }

    @Test
    void unexpectedErrorDoesNotLeakInternals() {
        MvcTestResult result = mvc.get().uri("/test/failure").exchange();

        assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        assertThat(result).bodyText().doesNotContain("internal detail");
    }

    @Test
    void unknownEndpointIsProblemDetail() {
        MvcTestResult result = mvc.get().uri("/no-such-endpoint").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private MvcTestResult post(String body) {
        return mvc.post().uri("/test/payload").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String detail) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
    }
}
