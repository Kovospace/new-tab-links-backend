package com.kovospace.newtablinks.environment.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.common.models.WorkspaceLocation;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.user.models.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A workspace: the top level of the link hierarchy, owned directly by a user.
 *
 * <p>The browser extension shows exactly one environment at a time and lets the user switch
 * between them, which is why an environment carries its own display order.</p>
 *
 * <p>An environment belongs to a {@link ProfileEntity} and, redundantly, straight to the profile's
 * owner. The redundancy is deliberate: every ownership-scoped query in this application - and
 * every ordered snapshot query - reaches the owner through this one join, and rerouting them all
 * through the profile would buy nothing but risk. {@link #getOwner()} is therefore never set
 * independently; it is always taken from the profile, which is what makes the two impossible to
 * disagree.</p>
 *
 * @since 0.0.1
 */
@Entity
@Table(name = "environment")
public class EnvironmentEntity extends AbstractAuditableEntity {

    /**
     * User this environment belongs to. Always the owner of {@link #profile}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity owner;

    /**
     * Profile this environment is filed under.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    /**
     * Name shown on the environment switcher.
     */
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * Free text describing the environment, or {@code null} when the user wrote none.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Zero based position among the owner's environments, ascending.
     */
    @Column(name = "position", nullable = false)
    private int position;

    /**
     * Required by JPA.
     */
    protected EnvironmentEntity() {
    }

    /**
     * Creates an environment inside a profile.
     *
     * <p>The owner is taken from the profile rather than accepted separately, so that an
     * environment can never end up filed under one user's profile while belonging to another.</p>
     *
     * @param profile     profile the environment is filed under
     * @param name        name shown on the environment switcher
     * @param description free text describing the environment, may be {@code null}
     * @param position    zero based position among the owner's environments
     */
    public EnvironmentEntity(
            final ProfileEntity profile,
            final String name,
            final String description,
            final int position) {

        this.profile = profile;
        this.owner = profile.getOwner();
        this.name = name;
        this.description = description;
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
     * Returns where this environment sits, for a plan-limit check.
     *
     * @return the account, profile and environment identifiers
     * @since 0.0.18
     */
    public WorkspaceLocation toWorkspaceLocation() {
        return new WorkspaceLocation(owner.getId(), profile.getId(), getId());
    }

    /**
     * Returns the profile this environment is filed under.
     *
     * @return the profile
     */
    public ProfileEntity getProfile() {
        return profile;
    }

    /**
     * Moves the environment into another profile, and its ownership along with it.
     *
     * @param profile the profile to file the environment under
     */
    public void setProfile(final ProfileEntity profile) {
        this.profile = profile;
        this.owner = profile.getOwner();
    }

    /**
     * Returns the free text describing the environment.
     *
     * @return the description, or {@code null} when the user wrote none
     */
    public String getDescription() {
        return description;
    }

    /**
     * Replaces the free text describing the environment.
     *
     * @param description the description to set, may be {@code null}
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Returns the name shown on the environment switcher.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Replaces the name shown on the environment switcher.
     *
     * @param name the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Returns the position among the owner's environments.
     *
     * @return the zero based position
     */
    public int getPosition() {
        return position;
    }

    /**
     * Replaces the position among the owner's environments.
     *
     * @param position the zero based position to set
     */
    public void setPosition(final int position) {
        this.position = position;
    }
}
