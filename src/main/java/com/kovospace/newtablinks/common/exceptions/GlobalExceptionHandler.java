package com.kovospace.newtablinks.common.exceptions;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

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
     * Renders a failed validation of a query or path parameter as HTTP 400.
     *
     * <p>Constraints written directly on a controller argument - as on the username existence
     * lookup - are enforced by the proxy that {@code @Validated} puts around the controller, and
     * surface as this exception rather than as a {@link MethodArgumentNotValidException}. Without
     * this handler they would fall through to the catch-all below and be answered with 500,
     * which is a lie: the caller sent a bad request and can fix it.</p>
     *
     * @param exception the exception raised by the constraint validator
     * @return a 400 response carrying the uniform error body and the per-parameter messages
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponseDto> handleParameterValidationFailure(
            final ConstraintViolationException exception) {

        final List<String> validationErrors = exception.getConstraintViolations()
                .stream()
                .map(GlobalExceptionHandler::describeConstraintViolation)
                .sorted(Comparator.naturalOrder())
                .toList();

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Request validation failed", validationErrors);
    }

    /**
     * Renders Spring's own method validation failure as HTTP 400.
     *
     * <p>The same failure as {@link #handleParameterValidationFailure(ConstraintViolationException)}
     * reported through the framework's built-in method validation, which is what runs when a
     * controller is not behind a {@code @Validated} proxy. Both shapes are handled because which
     * one appears depends on configuration rather than on anything the caller did - and this one
     * would otherwise reach the catch-all and be answered with 500.</p>
     *
     * @param exception the exception raised by the framework's method validation
     * @return a 400 response carrying the uniform error body and the per-parameter messages
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponseDto> handleHandlerMethodValidationFailure(
            final HandlerMethodValidationException exception) {

        return buildValidationErrorResponse(exception);
    }

    /**
     * Renders a method validation failure raised outside the web layer as HTTP 400.
     *
     * @param exception the exception raised by method validation on a service
     * @return a 400 response carrying the uniform error body and the per-parameter messages
     */
    @ExceptionHandler(MethodValidationException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMethodValidationFailure(
            final MethodValidationException exception) {

        return buildValidationErrorResponse(exception);
    }

    /**
     * Renders a missing required request parameter as HTTP 400.
     *
     * @param exception the exception raised when the parameter is absent altogether
     * @return a 400 response naming the missing parameter
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMissingRequestParameter(
            final MissingServletRequestParameterException exception) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                List.of("%s: is required".formatted(exception.getParameterName())));
    }

    /**
     * Renders a refused credential as HTTP 401, with the same wording whatever the real cause.
     *
     * @param exception the exception that was thrown
     * @return a 401 response carrying the uniform error body
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiErrorResponseDto> handleAuthenticationFailure(
            final AuthenticationFailedException exception) {

        return buildErrorResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), List.of());
    }

    /**
     * Renders an unusable token or code as HTTP 400.
     *
     * @param exception the exception that was thrown
     * @return a 400 response carrying the uniform error body
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiErrorResponseDto> handleInvalidToken(
            final InvalidTokenException exception) {

        return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), List.of());
    }

    /**
     * Renders a taken username as HTTP 409.
     *
     * @param exception the exception that was thrown
     * @return a 409 response carrying the uniform error body
     */
    /**
     * Renders a lockout as HTTP 429, saying how long it lasts.
     *
     * <p>Deliberately not 401. The credentials were never looked at, so answering as though they
     * had been would let a locked-out caller keep testing guesses and read the answer from which
     * refusal comes back.</p>
     *
     * @param exception the exception that was thrown
     * @return a 429 response carrying the uniform error body and a {@code Retry-After} header
     */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiErrorResponseDto> handleTooManyAttempts(
            final TooManyAttemptsException exception) {

        LOGGER.warn("Refusing an attempt that is locked out for another {}",
                exception.getRetryAfter());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(org.springframework.http.HttpHeaders.RETRY_AFTER,
                        Long.toString(Math.max(1L, exception.getRetryAfter().toSeconds())))
                .body(new ApiErrorResponseDto(
                        Instant.now(),
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        exception.getMessage(),
                        List.of()));
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<ApiErrorResponseDto> handleRegistrationConflict(
            final RegistrationConflictException exception) {

        return buildErrorResponse(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    /**
     * Renders an attempt to revoke a paid entitlement from the operator's screen as HTTP 409.
     *
     * @param exception the exception that was thrown
     * @return a 409 response carrying the uniform error body
     * @since 0.0.10
     */
    @ExceptionHandler(PaidEntitlementRevocationException.class)
    public ResponseEntity<ApiErrorResponseDto> handlePaidEntitlementRevocation(
            final PaidEntitlementRevocationException exception) {

        return buildErrorResponse(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    /**
     * Renders a payment feature this deployment has not been given credentials for as HTTP 503.
     *
     * @param exception the exception that was thrown
     * @return a 503 response carrying the uniform error body
     * @since 0.0.9
     */
    @ExceptionHandler(PaymentProviderNotConfiguredException.class)
    public ResponseEntity<ApiErrorResponseDto> handlePaymentProviderNotConfigured(
            final PaymentProviderNotConfiguredException exception) {

        LOGGER.warn("Refused a payment operation: {}", exception.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), List.of());
    }

    /**
     * Renders a failed call to the payment provider as HTTP 502.
     *
     * @param exception the exception that was thrown
     * @return a 502 response carrying the uniform error body
     * @since 0.0.9
     */
    @ExceptionHandler(PaymentProviderRequestFailedException.class)
    public ResponseEntity<ApiErrorResponseDto> handlePaymentProviderRequestFailed(
            final PaymentProviderRequestFailedException exception) {

        LOGGER.error("A call to the payment provider failed", exception);
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, exception.getMessage(), List.of());
    }

    /**
     * Renders a webhook delivery that is not signed with this service's secret as HTTP 401.
     *
     * @param exception the exception that was thrown
     * @return a 401 response carrying the uniform error body
     * @since 0.0.9
     */
    @ExceptionHandler(WebhookSignatureRejectedException.class)
    public ResponseEntity<ApiErrorResponseDto> handleWebhookSignatureRejected(
            final WebhookSignatureRejectedException exception) {

        LOGGER.warn("Refused a webhook delivery: {}", exception.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), List.of());
    }

    /**
     * Renders a signed webhook delivery that cannot be read as HTTP 400.
     *
     * @param exception the exception that was thrown
     * @return a 400 response carrying the uniform error body
     * @since 0.0.9
     */
    @ExceptionHandler(MalformedWebhookPayloadException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMalformedWebhookPayload(
            final MalformedWebhookPayloadException exception) {

        LOGGER.error("A correctly signed webhook delivery could not be read: {}",
                exception.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), List.of());
    }

    /**
     * Renders a redelivery of a webhook event still being processed as HTTP 409.
     *
     * @param exception the exception that was thrown
     * @return a 409 response carrying the uniform error body
     * @since 0.0.9
     */
    @ExceptionHandler(WebhookEventInFlightException.class)
    public ResponseEntity<ApiErrorResponseDto> handleWebhookEventInFlight(
            final WebhookEventInFlightException exception) {

        LOGGER.info("Deferred a webhook redelivery: {}", exception.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    /**
     * Renders anything not handled above as HTTP 500, without leaking internals to the caller.
     *
     * @param exception the unexpected exception
     * @return a 500 response carrying a deliberately generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponseDto> handleUnexpectedFailure(final Exception exception) {
        // Spring Security's own exceptions must reach its entry point to be turned into a
        // correct 401/403 with the right headers, so they are deliberately not swallowed here.
        if (exception instanceof org.springframework.security.access.AccessDeniedException
                || exception instanceof org.springframework.security.core.AuthenticationException) {
            throw (RuntimeException) exception;
        }
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
     * Turns a method validation result into the uniform 400 response.
     *
     * @param validationResult the failure reported by either flavour of method validation
     * @return a 400 response carrying the uniform error body and the per-parameter messages
     */
    private static ResponseEntity<ApiErrorResponseDto> buildValidationErrorResponse(
            final MethodValidationResult validationResult) {

        final List<String> validationErrors = validationResult.getAllErrors()
                .stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(message -> message != null)
                .sorted(Comparator.naturalOrder())
                .toList();

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Request validation failed", validationErrors);
    }

    /**
     * Formats a single violated constraint as {@code parameter: message}.
     *
     * <p>The property path of a method-level violation is qualified with the method name
     * ({@code checkUsernameExistence.username}); only the last node is shown, so the wording
     * matches what a body validation failure produces and no internal method name leaks.</p>
     *
     * @param violation the violated constraint
     * @return the formatted message
     */
    private static String describeConstraintViolation(final ConstraintViolation<?> violation) {
        final String propertyPath = violation.getPropertyPath().toString();
        final int lastSeparator = propertyPath.lastIndexOf('.');
        final String parameterName = lastSeparator < 0
                ? propertyPath
                : propertyPath.substring(lastSeparator + 1);

        return "%s: %s".formatted(parameterName, violation.getMessage());
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
