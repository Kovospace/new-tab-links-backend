package com.kovospace.newtablinks.subgroup.dtos;

import com.kovospace.newtablinks.subgroup.models.SubgroupCollapseState;

/**
 * The fields of a subgroup that a pushed synchronization operation replaces wholesale.
 *
 * <p>Distinct from {@link SubgroupSaveRequestDto} because synchronization sets the display
 * position as well; see
 * {@link com.kovospace.newtablinks.environment.dtos.EnvironmentSynchronizedValuesDto}.</p>
 *
 * @param name          title shown on the subgroup header
 * @param description   free text describing the subgroup, may be {@code null}
 * @param collapseState how the subgroup is folded now and how it starts out
 * @param position      zero based position among the group's subgroups
 * @since 0.0.6
 */
public record SubgroupSynchronizedValuesDto(
        String name,
        String description,
        SubgroupCollapseState collapseState,
        int position) {
}
