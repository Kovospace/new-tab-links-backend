package com.kovospace.newtablinks.common.exceptions;

import java.util.List;

/**
 * Thrown when the operator tries to "raise" an account's Fair Use limit to a value below the
 * global Fair Use limit.
 *
 * <p>An override only ever raises, so such a value is a mistake in the request, answered like a
 * rejected request field - 400, one {@code field: message} entry per offending limit, in the
 * same shape bean validation produces.</p>
 *
 * @since 0.0.20
 */
public class FairUseOverrideBelowGlobalLimitException extends RuntimeException {

    /** One {@code field: message} entry per offending limit. */
    private final transient List<String> validationErrors;

    /**
     * Creates the exception.
     *
     * @param validationErrors one {@code field: message} entry per offending limit; not empty
     */
    public FairUseOverrideBelowGlobalLimitException(final List<String> validationErrors) {
        super("A Fair Use override may only raise a limit: " + String.join("; ", validationErrors));
        this.validationErrors = List.copyOf(validationErrors);
    }

    /**
     * Returns the rejected fields.
     *
     * @return one {@code field: message} entry per offending limit, unmodifiable
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
