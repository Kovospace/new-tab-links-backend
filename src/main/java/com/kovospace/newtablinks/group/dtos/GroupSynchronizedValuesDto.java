package com.kovospace.newtablinks.group.dtos;

/**
 * The fields of a group that a pushed synchronization operation replaces wholesale.
 *
 * <p>Distinct from {@link GroupSaveRequestDto} because synchronization sets the display position
 * as well; see
 * {@link com.kovospace.newtablinks.environment.dtos.EnvironmentSynchronizedValuesDto}.</p>
 *
 * @param name        title shown on the group header
 * @param description free text describing the group, may be {@code null}
 * @param position    zero based position among the environment's groups
 * @since 0.0.6
 */
public record GroupSynchronizedValuesDto(String name, String description, int position) {
}
