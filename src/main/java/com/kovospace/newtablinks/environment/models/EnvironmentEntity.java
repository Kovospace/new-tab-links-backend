package com.kovospace.newtablinks.environment.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
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
 * @since 0.0.1
 */
@Entity
@Table(name = "environment")
public class EnvironmentEntity extends AbstractAuditableEntity {

    /**
     * User this environment belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity owner;

    /**
     * Name shown on the environment switcher.
     */
    @Column(name = "name", nullable = false, length = 120)
    private String name;

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
     * Creates an environment.
     *
     * @param owner    user the environment belongs to
     * @param name     name shown on the environment switcher
     * @param position zero based position among the owner's environments
     */
    public EnvironmentEntity(final UserEntity owner, final String name, final int position) {
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
