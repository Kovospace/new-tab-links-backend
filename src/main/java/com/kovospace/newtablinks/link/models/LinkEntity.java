package com.kovospace.newtablinks.link.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.group.models.GroupEntity;
import com.kovospace.newtablinks.subgroup.models.SubgroupEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A single bookmark rendered on the new tab page.
 *
 * <p>A link always belongs to a group. It may additionally belong to one of that group's
 * subgroups; when {@link #getParentSubgroup()} is {@code null} the link is rendered directly under the
 * group header. Keeping the group reference even for links inside a subgroup means a link can be
 * moved out of a subgroup without losing where it belongs.</p>
 *
 * @since 0.0.1
 */
@Entity
@Table(name = "link")
public class LinkEntity extends AbstractAuditableEntity {

    /**
     * Group this link belongs to. Always present.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private GroupEntity parentGroup;

    /**
     * Subgroup this link is nested in, or {@code null} when it sits directly in the group.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subgroup_id")
    private SubgroupEntity parentSubgroup;

    /**
     * Text shown for the link.
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * Address the link points to.
     */
    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    /**
     * Address of the cached favicon, or {@code null} when none has been resolved yet.
     */
    @Column(name = "favicon_url", length = 2048)
    private String faviconUrl;

    /**
     * Zero based position among the links of the same parent, ascending.
     */
    @Column(name = "position", nullable = false)
    private int position;

    /**
     * Required by JPA.
     */
    protected LinkEntity() {
    }

    /**
     * Creates a link.
     *
     * @param parentGroup    group the link belongs to
     * @param parentSubgroup subgroup the link is nested in, or {@code null} for a direct child of the group
     * @param title      text shown for the link
     * @param url        address the link points to
     * @param faviconUrl address of the cached favicon, may be {@code null}
     * @param position   zero based position among the links of the same parent
     */
    public LinkEntity(
            final GroupEntity parentGroup,
            final SubgroupEntity parentSubgroup,
            final String title,
            final String url,
            final String faviconUrl,
            final int position) {

        this.parentGroup = parentGroup;
        this.parentSubgroup = parentSubgroup;
        this.title = title;
        this.url = url;
        this.faviconUrl = faviconUrl;
        this.position = position;
    }

    /**
     * Returns the group this link belongs to.
     *
     * @return the group, never {@code null}
     */
    public GroupEntity getParentGroup() {
        return parentGroup;
    }

    /**
     * Returns the subgroup this link is nested in.
     *
     * @return the subgroup, or {@code null} when the link sits directly in the group
     */
    public SubgroupEntity getParentSubgroup() {
        return parentSubgroup;
    }

    /**
     * Moves the link into a subgroup, or out of any subgroup.
     *
     * @param parentSubgroup the subgroup to nest the link in, or {@code null} to detach it
     */
    public void setParentSubgroup(final SubgroupEntity parentSubgroup) {
        this.parentSubgroup = parentSubgroup;
    }

    /**
     * Returns the text shown for the link.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Replaces the text shown for the link.
     *
     * @param title the title to set
     */
    public void setTitle(final String title) {
        this.title = title;
    }

    /**
     * Returns the address the link points to.
     *
     * @return the URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Replaces the address the link points to.
     *
     * @param url the URL to set
     */
    public void setUrl(final String url) {
        this.url = url;
    }

    /**
     * Returns the address of the cached favicon.
     *
     * @return the favicon URL, or {@code null} when none has been resolved
     */
    public String getFaviconUrl() {
        return faviconUrl;
    }

    /**
     * Replaces the address of the cached favicon.
     *
     * @param faviconUrl the favicon URL to set, may be {@code null}
     */
    public void setFaviconUrl(final String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    /**
     * Returns the position among the links of the same parent.
     *
     * @return the zero based position
     */
    public int getPosition() {
        return position;
    }

    /**
     * Replaces the position among the links of the same parent.
     *
     * @param position the zero based position to set
     */
    public void setPosition(final int position) {
        this.position = position;
    }
}
