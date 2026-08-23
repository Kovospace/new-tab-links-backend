package com.kovospace.newtablinks.common.exceptions;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions thrown anywhere below the controller layer into {@link ApiErrorResponseDto}.
 *
 * <p>Keeping every translation here is what allows services to signal failure by throwing a
 * domain exception while remaining completely unaware of HTTP.</p>
 *
 * @since 0.0.1
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Renders a missing resource as HTTP 404.
     *
     * @param exception the exception that was thrown
     * @return a 404 response carrying the uniform error body
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleResourceNotFound(
            final ResourceNotFoundException exception) {

        LOGGER.debug("Resource not found: {}", exception.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    /**
     * Renders a failed bean-validation of a request body as HTTP 400, listing every rejected field.
     *
     * @param exception the exception raised by the validation of an {@code @Valid} argument
     * @return a 400 response carrying the uniform error body and the per-field messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDto> handleRequestValidationFailure(
            final MethodArgumentNotValidException exception) {

        final List<String> validationErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(GlobalExceptionHandler::describeFieldError)
                .sorted(Comparator.naturalOrder())
                .toList();

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Request validation failed", validationErrors);
    }

    /**
     * Renders anything not handled above as HTTP 500, without leaking internals to the caller.
     *
     * @param exception the unexpected exception
     * @return a 500 response carrying a deliberately generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponseDto> handleUnexpectedFailure(final Exception exception) {
        LOGGER.error("Unhandled exception reached the controller boundary", exception);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected internal error",
                List.of());
    }

    /**
     * Formats a single rejected field as {@code field: message}.
     *
     * @param fieldError the rejected field reported by the validator
     * @return the formatted message
     */
    private static String describeFieldError(final FieldError fieldError) {
        return "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

    /**
     * Assembles the response entity shared by every handler above.
     *
     * @param status           status to return
     * @param message          human readable explanation
     * @param validationErrors per-field messages, empty when not a validation failure
     * @return the assembled response
     */
    private static ResponseEntity<ApiErrorResponseDto> buildErrorResponse(
            final HttpStatus status,
            final String message,
            final List<String> validationErrors) {

        final ApiErrorResponseDto body = new ApiErrorResponseDto(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                validationErrors);

        return ResponseEntity.status(status).body(body);
    }
}
