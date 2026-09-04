package com.kovospace.newtablinks.profile.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

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
}
