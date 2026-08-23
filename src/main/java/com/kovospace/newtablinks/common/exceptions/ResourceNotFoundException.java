package com.kovospace.newtablinks.common.exceptions;

import java.util.UUID;

/**
 * Thrown when a resource addressed by identifier does not exist.
 *
 * <p>Services throw this instead of returning {@code null} or an empty {@link java.util.Optional}
 * to their callers, so that a missing resource cannot be mistaken for a valid empty result.
 * {@link GlobalExceptionHandler} translates it into HTTP 404.</p>
 *
 * @since 0.0.1
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates an exception describing which resource was not found.
     *
     * @param resourceName human readable name of the resource type, for example {@code "Link"}
     * @param resourceId   identifier that was looked up
     */
    public ResourceNotFoundException(final String resourceName, final UUID resourceId) {
        super("%s with id %s was not found".formatted(resourceName, resourceId));
    }
}
