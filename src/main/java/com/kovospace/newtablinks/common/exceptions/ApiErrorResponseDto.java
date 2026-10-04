package com.kovospace.newtablinks.common.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by every failing endpoint.
 *
 * <p>The last three components are optional and left out of the JSON entirely when absent, so
 * every body that does not carry them is byte-for-byte what it was before they existed.</p>
 *
 * @param timestamp        moment the failure was handled
 * @param status           HTTP status code that accompanies this body
 * @param error            short, stable description of the failure category
 * @param message          human readable explanation of what went wrong
 * @param validationErrors per-field validation messages; empty unless the failure was a
 *                         request validation failure
 * @param code             machine-readable failure code a client may branch on, {@code null}
 *                         (and omitted) for failures that have none
 * @param limit            the cap a refused write would have exceeded - a
 *                         {@code FairUseLimit} name with code {@code FAIR_USE_LIMIT_REACHED}, a
 *                         {@code FreePlanLimit} name with code {@code FREE_PLAN_LIMIT_REACHED};
 *                         absent otherwise
 * @param maximum          that cap's maximum, present only with one of those two codes
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
        List<String> validationErrors,

        @Schema(description = "Machine-readable failure code; absent when the failure has none",
                example = "FAIR_USE_LIMIT_REACHED", nullable = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String code,

        @Schema(description = "The cap that was reached. With code FAIR_USE_LIMIT_REACHED one "
                + "of LINKS_PER_WORKSPACE, WORKSPACES, PROFILES; with code "
                + "FREE_PLAN_LIMIT_REACHED one of WORKSPACES, PROFILES, DEVICES",
                example = "LINKS_PER_WORKSPACE", nullable = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String limit,

        @Schema(description = "The maximum of that cap; only with code FAIR_USE_LIMIT_REACHED "
                + "or FREE_PLAN_LIMIT_REACHED",
                example = "500", nullable = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer maximum) {

    /**
     * Creates a body without a failure code - the shape of every failure but a few.
     *
     * @param timestamp        moment the failure was handled
     * @param status           HTTP status code that accompanies this body
     * @param error            short, stable description of the failure category
     * @param message          human readable explanation of what went wrong
     * @param validationErrors per-field validation messages, empty when not a validation failure
     */
    public ApiErrorResponseDto(
            final Instant timestamp,
            final int status,
            final String error,
            final String message,
            final List<String> validationErrors) {

        this(timestamp, status, error, message, validationErrors, null, null, null);
    }
}
