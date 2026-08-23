package com.kovospace.newtablinks.user.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Owner of everything this service stores.
 *
 * <p>Every environment, and therefore transitively every group, subgroup and link, belongs to
 * exactly one user. The user is the boundary of a synchronization: a client syncs one user's
 * data and never sees another's.</p>
 *
 * <p>Authentication is not part of this service yet, so this entity carries identity only —
 * no credentials, no tokens.</p>
 *
 * @since 0.0.1
 */
@Entity
@Table(name = "app_user")
public class UserEntity extends AbstractAuditableEntity {

    /**
     * Address identifying the user, unique across the whole service.
     */
    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    /**
     * Name shown in the user interface. Free text, not unique.
     */
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /**
     * Required by JPA.
     */
    protected UserEntity() {
    }

    /**
     * Creates a user.
     *
     * @param email       address identifying the user
     * @param displayName name shown in the user interface
     */
    public UserEntity(final String email, final String displayName) {
        this.email = email;
        this.displayName = displayName;
    }

    /**
     * Returns the address identifying the user.
     *
     * @return the email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Replaces the address identifying the user.
     *
     * @param email the email address to set
     */
    public void setEmail(final String email) {
        this.email = email;
    }

    /**
     * Returns the name shown in the user interface.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Replaces the name shown in the user interface.
     *
     * @param displayName the display name to set
     */
    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }
}
