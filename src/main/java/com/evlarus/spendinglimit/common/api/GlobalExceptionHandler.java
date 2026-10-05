package com.evlarus.spendinglimit.common.api;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Converts every error into an RFC 9457 {@link ProblemDetail} ({@code application/problem+json}).
 * Standard Spring MVC errors (404, 405, 415, ...) are handled by {@link ResponseEntityExceptionHandler};
 * request validation and unreadable bodies get field-level details; anything unexpected becomes a 500
 * without leaking internal messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String ERRORS_PROPERTY = "errors";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final JsonPropertyNames JSON_NAMES = new JsonPropertyNames();

    /** Invalid {@code @Valid @RequestBody}. */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(JSON_NAMES.of(error.getField()), message(error)))
                .sorted(FieldViolation.ORDER)
                .toList();
        return validationProblem(ex, violations, headers, status, request);
    }

    /** Invalid {@code @RequestParam} / {@code @PathVariable} constraints (built-in method validation). */
    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "parameter"),
                                message(error))))
                .sorted(FieldViolation.ORDER)
                .toList();
        return validationProblem(ex, violations, headers, status, request);
    }

    /** Malformed JSON, unknown property, wrong value type. */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, describeUnreadableBody(ex));
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    private @Nullable ResponseEntity<Object> validationProblem(
            Exception ex, List<FieldViolation> violations, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        problem.setProperty(ERRORS_PROPERTY, violations);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static String describeUnreadableBody(HttpMessageNotReadableException ex) {
        return switch (ex.getMostSpecificCause()) {
            case UnrecognizedPropertyException e -> "Unknown property '%s'".formatted(e.getPropertyName());
            case MismatchedInputException e when !e.getPath().isEmpty() ->
                    "Invalid value for property '%s'".formatted(jsonPath(e.getPath()));
            case JacksonException e -> "Malformed JSON request body";
            default -> "Request body is missing or unreadable";
        };
    }

    /** {@code [items, 0, sum]} -> {@code items[0].sum}. Names are already JSON names. */
    private static String jsonPath(List<JacksonException.Reference> path) {
        StringBuilder result = new StringBuilder();
        for (JacksonException.Reference reference : path) {
            if (reference.getPropertyName() != null) {
                if (!result.isEmpty()) {
                    result.append('.');
                }
                result.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                result.append('[').append(reference.getIndex()).append(']');
            }
        }
        return result.toString();
    }

    private static String message(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid";
    }

    /** One violated constraint; {@code field} uses the JSON (snake_case) name the client sent. */
    public record FieldViolation(String field, String message) {

        static final Comparator<FieldViolation> ORDER =
                Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message);
    }

    /**
     * Translates Java property paths ({@code accountFrom}, {@code items[0].unitPrice}) to JSON names with
     * exactly the algorithm Jackson uses for {@code SNAKE_CASE}, so error field names always match the request.
     * Jackson 3 keeps {@code translate} protected, hence the subclass.
     */
    private static final class JsonPropertyNames extends PropertyNamingStrategies.SnakeCaseStrategy {

        String of(String javaPath) {
            StringBuilder result = new StringBuilder();
            for (String segment : javaPath.split("\\.")) {
                if (!result.isEmpty()) {
                    result.append('.');
                }
                int index = segment.indexOf('[');
                String name = index < 0 ? segment : segment.substring(0, index);
                result.append(translate(name));
                if (index >= 0) {
                    result.append(segment.substring(index));
                }
            }
            return result.toString();
        }
    }
}
