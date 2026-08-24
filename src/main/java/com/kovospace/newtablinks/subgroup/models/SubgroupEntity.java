package com.kovospace.newtablinks.subgroup.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.group.models.GroupEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A collapsible section inside a group.
 *
 * <p>Subgroups are optional: a group's links may sit directly in the group, in one of its
 * subgroups, or both. Whether a subgroup is folded away is per user state and therefore stored
 * with the subgroup itself.</p>
 *
 * @since 0.0.1
 */
@Entity
@Table(name = "link_subgroup")
public class SubgroupEntity extends AbstractAuditableEntity {

    /**
     * Group this subgroup is nested in.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private GroupEntity parentGroup;

    /**
     * Title shown on the subgroup header.
     */
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * Zero based position among the group's subgroups, ascending.
     */
    @Column(name = "position", nullable = false)
    private int position;

    /**
     * Whether the subgroup is currently folded away in the user interface.
     */
    @Column(name = "collapsed", nullable = false)
    private boolean collapsed;

    /**
     * Required by JPA.
     */
    protected SubgroupEntity() {
    }

    /**
     * Creates a subgroup.
     *
     * @param parentGroup group the subgroup is nested in
     * @param name      title shown on the subgroup header
     * @param position  zero based position among the group's subgroups
     * @param collapsed whether the subgroup starts folded away
     */
    public SubgroupEntity(
            final GroupEntity parentGroup,
            final String name,
            final int position,
            final boolean collapsed) {

        this.parentGroup = parentGroup;
        this.name = name;
        this.position = position;
        this.collapsed = collapsed;
    }

    /**
     * Returns the group this subgroup is nested in.
     *
     * @return the group
     */
    public GroupEntity getParentGroup() {
        return parentGroup;
    }

    /**
     * Returns the title shown on the subgroup header.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Replaces the title shown on the subgroup header.
     *
     * @param name the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Returns the position among the group's subgroups.
     *
     * @return the zero based position
     */
    public int getPosition() {
        return position;
    }

    /**
     * Replaces the position among the group's subgroups.
     *
     * @param position the zero based position to set
     */
    public void setPosition(final int position) {
        this.position = position;
    }

    /**
     * Tells whether the subgroup is currently folded away.
     *
     * @return {@code true} when collapsed
     */
    public boolean isCollapsed() {
        return collapsed;
    }

    /**
     * Sets whether the subgroup is folded away.
     *
     * @param collapsed {@code true} to collapse the subgroup
     */
    public void setCollapsed(final boolean collapsed) {
        this.collapsed = collapsed;
    }
}
