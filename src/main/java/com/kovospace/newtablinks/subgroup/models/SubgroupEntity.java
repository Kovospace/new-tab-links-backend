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
     * Whether tabs navigating to this subgroup's links are pulled into its browser tab group.
     *
     * <p>A property of the subgroup rather than of the device that switched it on, which is why it
     * is stored here and travels with the row. Not a constructor argument: like the description, it
     * is off until somebody sets it, and a fifth positional boolean would be one more thing to
     * transpose by accident.</p>
     */
    @Column(name = "catch_links_into_tab_group", nullable = false)
    private boolean catchLinksIntoTabGroup;

    /**
     * Name of the Chrome tab group colour this subgroup is painted with, or {@code null}.
     *
     * <p>Nullable rather than defaulted, unlike the flags above: an absent colour is not "no
     * colour" but a subgroup nobody has given one yet - which is what every subgroup stored
     * before this column existed is.</p>
     *
     * <p>Plain text on purpose, not an enumeration. The vocabulary belongs to Chrome - grey,
     * blue, red, yellow, green, pink, purple, cyan, orange - so a tenth colour in some future
     * release has to be storable without a release of this application. The length cap is the
     * only rule this side imposes.</p>
     */
    @Column(name = "color", length = 16)
    private String color;

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

    /**
     * Tells whether tabs navigating to this subgroup's links are pulled into its tab group.
     *
     * @return {@code true} when such tabs are caught
     */
    public boolean isCatchLinksIntoTabGroup() {
        return catchLinksIntoTabGroup;
    }

    /**
     * Sets whether tabs navigating to this subgroup's links are pulled into its tab group.
     *
     * @param catchLinksIntoTabGroup {@code true} to catch such tabs
     */
    public void setCatchLinksIntoTabGroup(final boolean catchLinksIntoTabGroup) {
        this.catchLinksIntoTabGroup = catchLinksIntoTabGroup;
    }

    /**
     * Returns the name of the Chrome tab group colour this subgroup is painted with.
     *
     * @return the colour name, or {@code null} when the subgroup has never been given one
     */
    public String getColor() {
        return color;
    }

    /**
     * Replaces the name of the Chrome tab group colour this subgroup is painted with.
     *
     * @param color the colour name to set, may be {@code null} to leave the subgroup without one
     */
    public void setColor(final String color) {
        this.color = color;
    }
}
