package com.kovospace.newtablinks.group.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.environment.models.EnvironmentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A titled box of links inside an environment.
 *
 * <p>A group holds links directly and may additionally organise some of them into subgroups.</p>
 *
 * @since 0.0.1
 */
@Entity
@Table(name = "link_group")
public class GroupEntity extends AbstractAuditableEntity {

    /**
     * Environment this group is displayed in.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false)
    private EnvironmentEntity environment;

    /**
     * Title shown on the group header.
     */
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * Zero based position among the environment's groups, ascending.
     */
    @Column(name = "position", nullable = false)
    private int position;

    /**
     * Required by JPA.
     */
    protected GroupEntity() {
    }

    /**
     * Creates a group.
     *
     * @param environment environment the group is displayed in
     * @param name        title shown on the group header
     * @param position    zero based position among the environment's groups
     */
    public GroupEntity(final EnvironmentEntity environment, final String name, final int position) {
        this.environment = environment;
        this.name = name;
        this.position = position;
    }

    /**
     * Returns the environment this group belongs to.
     *
     * @return the environment
     */
    public EnvironmentEntity getEnvironment() {
        return environment;
    }

    /**
     * Returns the title shown on the group header.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Replaces the title shown on the group header.
     *
     * @param name the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Returns the position among the environment's groups.
     *
     * @return the zero based position
     */
    public int getPosition() {
        return position;
    }

    /**
     * Replaces the position among the environment's groups.
     *
     * @param position the zero based position to set
     */
    public void setPosition(final int position) {
        this.position = position;
    }
}
