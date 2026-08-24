package com.kovospace.newtablinks.common.exceptions;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by every failing endpoint.
 *
 * @param timestamp        moment the failure was handled
 * @param status           HTTP status code that accompanies this body
 * @param error            short, stable description of the failure category
 * @param message          human readable explanation of what went wrong
 * @param validationErrors per-field validation messages; empty unless the failure was a
 *                         request validation failure
 * @since 0.0.1
 */
@Schema(description = "Uniform error body returned by every failing endpoint")
public record ApiErrorResponseDto(

        @Schema(description = "Moment the failure was handled", example = "2026-08-24T10:15:30Z")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "Short description of the failure category", example = "Not Found")
        String error,

        @Schema(description = "Human readable explanation", example = "Link with id 6f1c… was not found")
        String message,

        @Schema(description = "Per-field validation messages, empty when not a validation failure")
        List<String> validationErrors) {
}
