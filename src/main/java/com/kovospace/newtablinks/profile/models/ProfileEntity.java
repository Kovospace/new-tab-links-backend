package com.kovospace.newtablinks.profile.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A named set of environments, and the new top of the link hierarchy.
 *
 * <p>The browser extension has always grouped environments into profiles locally; the server
 * only learned about them when two-way synchronization arrived. A profile is a partition of the
 * user's own data and nothing more - it is not shared, not published and not a second account.</p>
 *
 * <p>Which profile a device is currently showing is deliberately <em>not</em> stored here. That
 * is a property of the device, not of the account, and syncing it would make every browser jump
 * to whatever another browser was last looking at.</p>
 *
 * @since 0.0.6
 */
@Entity
@Table(name = "profile")
public class ProfileEntity extends AbstractAuditableEntity {

    /**
     * User this profile belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity owner;

    /**
     * Name shown on the profile switcher.
     */
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * Zero based position among the owner's profiles, ascending.
     */
    @Column(name = "position", nullable = false)
    private int position;

    /**
     * Whether this profile lets its links and groups be rearranged by dragging.
     *
     * <p>The first of the profile's settings, and the reason they ride on this row rather than in
     * a table of their own: a setting describes the profile, so it has to survive a pull, which
     * replaces a profile wholesale from the snapshot. Not a constructor argument, for the reason
     * given on {@link com.kovospace.newtablinks.subgroup.models.SubgroupEntity}'s tab group
     * setting - it is off until somebody sets it, and a third positional argument would be one
     * more thing to transpose by accident.</p>
     */
    @Column(name = "enable_drag_and_drop", nullable = false)
    private boolean enableDragAndDrop;

    /**
     * Whether this profile has dismissed the tips shown on the new tab page background.
     *
     * <p>The second of the profile's settings, and stored the same way as the first. Named for
     * hiding rather than for showing because the column is {@code NOT NULL DEFAULT false}: every
     * profile that existed before it did reads as {@code false}, and {@code false} therefore has
     * to be the state those profiles are already in - tips visible.</p>
     */
    @Column(name = "hide_tips", nullable = false)
    private boolean hideTips;

    /**
     * Whether this profile records the browser tabs its user closes.
     *
     * <p>On unless somebody turns it off, unlike the settings above: the column is
     * {@code NOT NULL DEFAULT true}, because recording is what every profile did before the
     * setting existed. The field starts {@code true} for the same reason, so that a profile
     * created by a client that has never heard of the setting records, as it always did.
     * Turning it off deletes nothing here - the client removes its closed tabs itself and pushes
     * the deletions.</p>
     */
    @Column(name = "record_closed_tabs", nullable = false)
    private boolean recordClosedTabs = true;

    /**
     * Identifiers of the tips this profile has dismissed one by one, in the order the client sent.
     *
     * <p>A tip identifier is a stable name out of the extension's own list of tips, not a row
     * anything here refers to, so it is kept as an array on the profile rather than in a table
     * of its own: it is only ever read and replaced together with the profile. Never
     * {@code null}; the column defaults to an empty array.</p>
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dismissed_tips", nullable = false)
    private List<String> dismissedTips = new ArrayList<>();

    /**
     * Required by JPA.
     */
    protected ProfileEntity() {
    }

    /**
     * Creates a profile.
     *
     * @param owner    user the profile belongs to
     * @param name     name shown on the profile switcher
     * @param position zero based position among the owner's profiles
     */
    public ProfileEntity(final UserEntity owner, final String name, final int position) {
        this.owner = owner;
        this.name = name;
        this.position = position;
    }

    /**
     * Returns the owning user.
     *
     * @return the owner
     */
    public UserEntity getOwner() {
        return owner;
    }

    /**
     * Returns the name shown on the profile switcher.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Replaces the name shown on the profile switcher.
     *
     * @param name the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Returns the position among the owner's profiles.
     *
     * @return the zero based position
     */
    public int getPosition() {
        return position;
    }

    /**
     * Replaces the position among the owner's profiles.
     *
     * @param position the zero based position to set
     */
    public void setPosition(final int position) {
        this.position = position;
    }

    /**
     * Tells whether this profile lets its links and groups be rearranged by dragging.
     *
     * @return {@code true} when dragging rearranges them
     */
    public boolean isEnableDragAndDrop() {
        return enableDragAndDrop;
    }

    /**
     * Sets whether this profile lets its links and groups be rearranged by dragging.
     *
     * @param enableDragAndDrop {@code true} to let dragging rearrange them
     */
    public void setEnableDragAndDrop(final boolean enableDragAndDrop) {
        this.enableDragAndDrop = enableDragAndDrop;
    }

    /**
     * Tells whether this profile hides the tips shown on the new tab page background.
     *
     * @return {@code true} when the tips have been dismissed and are not shown
     */
    public boolean isHideTips() {
        return hideTips;
    }

    /**
     * Sets whether this profile hides the tips shown on the new tab page background.
     *
     * @param hideTips {@code true} to keep the tips dismissed
     */
    public void setHideTips(final boolean hideTips) {
        this.hideTips = hideTips;
    }

    /**
     * Tells whether this profile records the browser tabs its user closes.
     *
     * @return {@code true} when closed tabs are recorded, which is the default
     */
    public boolean isRecordClosedTabs() {
        return recordClosedTabs;
    }

    /**
     * Sets whether this profile records the browser tabs its user closes.
     *
     * @param recordClosedTabs {@code true} to record them
     */
    public void setRecordClosedTabs(final boolean recordClosedTabs) {
        this.recordClosedTabs = recordClosedTabs;
    }

    /**
     * Sets whether this profile records closed tabs, but only when the caller said so.
     *
     * <p>A client released before the setting existed sends nothing for it. Read as
     * {@code false}, that silence would switch recording off on every profile such a client
     * saves - so an absent value leaves the setting as it is, which for a new profile is on.</p>
     *
     * @param suppliedRecordClosedTabs the value the caller sent; {@code null} changes nothing
     */
    public void setRecordClosedTabsWhenSupplied(final Boolean suppliedRecordClosedTabs) {
        if (suppliedRecordClosedTabs != null) {
            this.recordClosedTabs = suppliedRecordClosedTabs;
        }
    }

    /**
     * Returns the identifiers of the tips this profile has dismissed one by one.
     *
     * @return the identifiers in the order they were stored, never {@code null}; unmodifiable
     */
    public List<String> getDismissedTips() {
        return List.copyOf(dismissedTips);
    }

    /**
     * Replaces the identifiers of the tips this profile has dismissed one by one.
     *
     * @param dismissedTips the identifiers to store, in order; {@code null} is stored as none
     */
    public void setDismissedTips(final List<String> dismissedTips) {
        this.dismissedTips = dismissedTips == null ? new ArrayList<>() : new ArrayList<>(dismissedTips);
    }
}
