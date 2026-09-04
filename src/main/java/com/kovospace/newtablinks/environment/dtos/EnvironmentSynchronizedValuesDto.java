package com.kovospace.newtablinks.environment.dtos;

/**
 * The fields of an environment that a pushed synchronization operation replaces wholesale.
 *
 * <p>Distinct from {@link EnvironmentSaveRequestDto} because synchronization sets the display
 * position as well, and because it is not an HTTP body and carries no validation annotations -
 * the operation it came from was validated at the controller boundary.</p>
 *
 * @param name        name shown on the environment switcher
 * @param description free text describing the environment, may be {@code null}
 * @param position    zero based position among the owner's environments
 * @since 0.0.6
 */
public record EnvironmentSynchronizedValuesDto(String name, String description, int position) {
}
