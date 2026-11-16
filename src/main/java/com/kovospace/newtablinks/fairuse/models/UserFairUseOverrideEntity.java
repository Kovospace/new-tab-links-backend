package com.kovospace.newtablinks.fairuse.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The Fair Use limits the operator raised for one account, with the reason given.
 *
 * <p>Keyed by the account itself rather than by an identifier of its own: an account has at most
 * one override, and the migration cascades the row away with the account. The account is held
 * as a plain identifier rather than a relation, because nothing here ever needs the account row
 * and a relation would only add a join.</p>
 *
 * <p>A row always raises at least one limit - an override with every limit {@code null} is
 * deleted instead of stored - so the presence of a row is what "this account has an override"
 * means.</p>
 *
 * @since 0.0.20
 */
@Entity
@Table(name = "user_fair_use_override")
public class UserFairUseOverrideEntity {

    /** Longest note the column holds. */
    public static final int NOTE_MAXIMUM_LENGTH = 500;

    /** Identifier of the account the override belongs to. */
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Raised profile limit, or {@code null}. */
    @Column(name = "profiles")
    private Integer profiles;

    /** Raised workspaces-per-profile limit, or {@code null}. */
    @Column(name = "workspaces_per_profile")
    private Integer workspacesPerProfile;

    /** Raised groups-per-workspace limit, or {@code null}. */
    @Column(name = "groups_per_workspace")
    private Integer groupsPerWorkspace;

    /** Raised subgroups-per-group limit, or {@code null}. */
    @Column(name = "subgroups_per_group")
    private Integer subgroupsPerGroup;

    /** Raised links-per-workspace limit, or {@code null}. */
    @Column(name = "links_per_workspace")
    private Integer linksPerWorkspace;

    /** Raised closed-tab history limit, or {@code null}. */
    @Column(name = "closed_tabs")
    private Integer closedTabs;

    /** Raised browsing history limit, or {@code null}. */
    @Column(name = "browsing_history")
    private Integer browsingHistory;

    /** Raised signed-in installation limit, or {@code null}. */
    @Column(name = "devices")
    private Integer devices;

    /** The operator's reason for the raise, or {@code null}. */
    @Column(name = "note", length = NOTE_MAXIMUM_LENGTH)
    private String note;

    /** When the operator last changed the override. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Required by JPA.
     */
    protected UserFairUseOverrideEntity() {
    }

    /**
     * Creates an empty override for an account, to be filled by {@link #replaceWith}.
     *
     * @param userId identifier of the account
     */
    public UserFairUseOverrideEntity(final UUID userId) {
        this.userId = userId;
    }

    /**
     * Replaces every raised limit and the note, and stamps the change.
     *
     * @param overrides the new overrides; {@code null} values mean "not raised"
     * @param note      the operator's reason, or {@code null}
     */
    public void replaceWith(final FairUseLimitOverrides overrides, final String note) {
        this.profiles = overrides.profiles();
        this.workspacesPerProfile = overrides.workspacesPerProfile();
        this.groupsPerWorkspace = overrides.groupsPerWorkspace();
        this.subgroupsPerGroup = overrides.subgroupsPerGroup();
        this.linksPerWorkspace = overrides.linksPerWorkspace();
        this.closedTabs = overrides.closedTabs();
        this.browsingHistory = overrides.browsingHistory();
        this.devices = overrides.devices();
        this.note = note;
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the raised limits as a value.
     *
     * @return the overrides, {@code null} where a limit is not raised
     */
    public FairUseLimitOverrides toOverrides() {
        return new FairUseLimitOverrides(
                profiles,
                workspacesPerProfile,
                groupsPerWorkspace,
                subgroupsPerGroup,
                linksPerWorkspace,
                closedTabs,
                browsingHistory,
                devices);
    }

    /**
     * Returns the identifier of the account.
     *
     * @return the account's identifier
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * Returns the operator's reason for the raise.
     *
     * @return the note, or {@code null} when none was given
     */
    public String getNote() {
        return note;
    }
}
