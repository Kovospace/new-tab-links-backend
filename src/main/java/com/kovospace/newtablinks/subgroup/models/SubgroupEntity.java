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
     * Free text describing the subgroup, or {@code null} when the user wrote none.
     */
    @Column(name = "description", length = 500)
    private String description;

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
     * Whether the subgroup starts folded away when a page is freshly opened.
     *
     * <p>A different question from {@link #collapsed}, which records how the user last left it.
     * The browser extension has always distinguished the two: a section can be one a person keeps
     * folded by default and still be open right now.</p>
     */
    @Column(name = "default_collapsed", nullable = false)
    private boolean defaultCollapsed;

    /**
     * Required by JPA.
     */
    protected SubgroupEntity() {
    }

    /**
     * Creates a subgroup.
     *
     * @param parentGroup      group the subgroup is nested in
     * @param name             title shown on the subgroup header
     * @param position         zero based position among the group's subgroups
     * @param collapseState    how the subgroup is folded now and how it starts out
     */
    public SubgroupEntity(
            final GroupEntity parentGroup,
            final String name,
            final int position,
            final SubgroupCollapseState collapseState) {

        this.parentGroup = parentGroup;
        this.name = name;
        this.position = position;
        this.collapsed = collapseState.collapsed();
        this.defaultCollapsed = collapseState.defaultCollapsed();
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
     * Moves the subgroup into another group.
     *
     * @param parentGroup the group to nest the subgroup in
     */
    public void setParentGroup(final GroupEntity parentGroup) {
        this.parentGroup = parentGroup;
    }

    /**
     * Returns the free text describing the subgroup.
     *
     * @return the description, or {@code null} when the user wrote none
     */
    public String getDescription() {
        return description;
    }

    /**
     * Replaces the free text describing the subgroup.
     *
     * @param description the description to set, may be {@code null}
     */
    public void setDescription(final String description) {
        this.description = description;
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

    /**
     * Tells whether the subgroup starts folded away on a freshly opened page.
     *
     * @return {@code true} when it starts collapsed
     */
    public boolean isDefaultCollapsed() {
        return defaultCollapsed;
    }

    /**
     * Sets whether the subgroup starts folded away on a freshly opened page.
     *
     * @param defaultCollapsed {@code true} to start the subgroup collapsed
     */
    public void setDefaultCollapsed(final boolean defaultCollapsed) {
        this.defaultCollapsed = defaultCollapsed;
    }
}
