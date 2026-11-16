package com.kovospace.newtablinks.fairuse.models;

import com.kovospace.newtablinks.common.models.PlanLimitValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The global Fair Use Policy limits, as the operator last set them - one row.
 *
 * <p>Like {@code AdminSignInLockEntity}, the table holds exactly one row ({@link #SINGLE_ROW_ID},
 * enforced by a CHECK in the migration), so it carries none of the audit columns of
 * {@code AbstractAuditableEntity}. The migration seeds it with the values configured under
 * {@code newtablinks.plan-limits.premium.*}; the backend falls back to that configuration only
 * when the row is missing.</p>
 *
 * @since 0.0.20
 */
@Entity
@Table(name = "fair_use_limits")
public class FairUseLimitsEntity {

    /** The identifier of the one row the table may hold. */
    public static final short SINGLE_ROW_ID = 1;

    /** Always {@link #SINGLE_ROW_ID}. */
    @Id
    @Column(name = "id", nullable = false)
    private short id;

    /** Most profiles an account may synchronise. */
    @Column(name = "profiles", nullable = false)
    private int profiles;

    /** Most workspaces one profile may synchronise. */
    @Column(name = "workspaces_per_profile", nullable = false)
    private int workspacesPerProfile;

    /** Most groups one workspace may hold. */
    @Column(name = "groups_per_workspace", nullable = false)
    private int groupsPerWorkspace;

    /** Most subgroups one group may hold. */
    @Column(name = "subgroups_per_group", nullable = false)
    private int subgroupsPerGroup;

    /** Most links one workspace may hold. */
    @Column(name = "links_per_workspace", nullable = false)
    private int linksPerWorkspace;

    /** Most closed-tab history entries an account keeps. */
    @Column(name = "closed_tabs", nullable = false)
    private int closedTabs;

    /** Most browsing history entries an account keeps. */
    @Column(name = "browsing_history", nullable = false)
    private int browsingHistory;

    /** Most extension installations signed in at once. */
    @Column(name = "devices", nullable = false)
    private int devices;

    /** When the operator last changed the row. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Required by JPA.
     */
    protected FairUseLimitsEntity() {
    }

    /**
     * Creates the single row holding the given limits, for when the migration's seed is missing.
     *
     * @param limits the limits to store
     * @return a new, not yet persisted row
     */
    public static FairUseLimitsEntity createSingleRow(final PlanLimitValues limits) {
        final FairUseLimitsEntity entity = new FairUseLimitsEntity();
        entity.id = SINGLE_ROW_ID;
        entity.replaceLimitsWith(limits);
        return entity;
    }

    /**
     * Replaces every limit and stamps the change.
     *
     * @param limits the new limits, already validated as positive by {@link PlanLimitValues}
     */
    public void replaceLimitsWith(final PlanLimitValues limits) {
        this.profiles = limits.profiles();
        this.workspacesPerProfile = limits.workspacesPerProfile();
        this.groupsPerWorkspace = limits.groupsPerWorkspace();
        this.subgroupsPerGroup = limits.subgroupsPerGroup();
        this.linksPerWorkspace = limits.linksPerWorkspace();
        this.closedTabs = limits.closedTabs();
        this.browsingHistory = limits.browsingHistory();
        this.devices = limits.devices();
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the stored limits as a value.
     *
     * @return every limit of the row
     * @throws IllegalArgumentException when the row holds a limit below one, which the
     *                                  migration's CHECK constraints rule out
     */
    public PlanLimitValues toPlanLimitValues() {
        return new PlanLimitValues(
                profiles,
                workspacesPerProfile,
                groupsPerWorkspace,
                subgroupsPerGroup,
                linksPerWorkspace,
                closedTabs,
                browsingHistory,
                devices);
    }
}
