package com.kovospace.newtablinks.user.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One place a user has signed in from: a machine and a browser on it.
 *
 * <p>The same physical machine running three browsers is three devices, because that is what the
 * user actually experiences - each browser holds its own tokens and is signed out separately.
 * What identifies one is {@link #getInstallationId()}, the identifier the client minted for
 * itself and keeps for as long as it stays installed; signing in again from the same installation
 * updates this row rather than adding another.</p>
 *
 * <p>The pair {@code (deviceName, browserName)} used to be that identifier, and could not carry
 * it. Neither half distinguishes two browsers on one machine: a device name comes from
 * {@code navigator.platform}, which is frozen and names the operating system, and Chromium forks
 * impersonate Chrome in the user agent deliberately. Two Chromium browsers on one machine
 * therefore shared a row - the list undercounted, and signing that device out revoked the tokens
 * of both, because tokens hang off the row they collided on. The pair is still the fallback for
 * clients that have no installation identity, the website among them.</p>
 *
 * <p><strong>This is a history, not a list of live sessions.</strong> A device stays listed after
 * its tokens expire or are revoked, because the useful question is "where has my account been
 * used", which an expiring session list cannot answer. {@link #getLastUsedAt()} is what tells
 * the two apart.</p>
 *
 * <p>It also exists for a mechanical reason: refresh tokens rotate on every use, so a client
 * refreshing every fifteen minutes would otherwise leave thousands of token rows per month with
 * nothing tying them together. Tokens hang off the device, and the device is what a user sees.</p>
 *
 * <p><strong>No {@code uniqueConstraints} here, deliberately.</strong> The real rule is two
 * <em>partial</em> unique indexes - one on {@code (user_id, installation_id)} where an
 * installation is set, one on {@code (user_id, device_name, browser_name)} where it is not - and
 * JPA cannot express either. Declaring the first half as a plain constraint would state something
 * weaker than the truth, generate an object that collides by name with the migrated one, and
 * still not be checked: {@code ddl-auto=validate} compares columns and types and never looks at
 * constraints. The rule lives in {@code new-tab-links-migrations}, V4, and is enforced there.</p>
 *
 * @since 0.0.3
 */
@Entity
@Table(name = "user_device")
public class UserDeviceEntity extends AbstractAuditableEntity {

    /**
     * Account this device belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * Name of the machine, as supplied by the client.
     *
     * <p>A browser cannot read its host's name, so this is whatever the client chose to send -
     * typically something the user typed once. It is a label, never an identifier to trust.</p>
     */
    @Column(name = "device_name", nullable = false, length = 120)
    private String deviceName;

    /**
     * Browser the sign-in came from, derived from the {@code User-Agent} header.
     */
    @Column(name = "browser_name", nullable = false, length = 60)
    private String browserName;

    /**
     * Identifier the client installation minted for itself, or {@code null} when it has none.
     *
     * <p>Null for two kinds of row and both are legitimate: those created before installations
     * were reported, and those created by the website, which is not an installation. The migrated
     * schema enforces uniqueness on {@code (user_id, installation_id)} only where this is set,
     * and keeps the old name-based rule for the rest.</p>
     */
    @Column(name = "installation_id")
    private UUID installationId;

    /**
     * Moment this device was last used to obtain or refresh a session.
     */
    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    /**
     * Required by JPA.
     */
    protected UserDeviceEntity() {
    }

    /**
     * Records a device the first time it is seen.
     *
     * @param user           account the device belongs to
     * @param deviceName     name of the machine as supplied by the client
     * @param browserName    browser derived from the user agent
     * @param installationId identifier the installation minted for itself, may be {@code null}
     * @param lastUsedAt     moment of this first use
     */
    public UserDeviceEntity(
            final UserEntity user,
            final String deviceName,
            final String browserName,
            final UUID installationId,
            final Instant lastUsedAt) {

        this.user = user;
        this.deviceName = deviceName;
        this.browserName = browserName;
        this.installationId = installationId;
        this.lastUsedAt = lastUsedAt;
    }

    /**
     * Returns the installation that reported this device.
     *
     * @return the identifier, or {@code null} for a row no installation has claimed
     */
    public UUID getInstallationId() {
        return installationId;
    }

    /**
     * Attaches an installation to a row that has none.
     *
     * <p>How a device recorded before installations were reported survives the change: the first
     * sign-in that names an installation and matches this row by name claims it, so the user
     * keeps one device with its history rather than gaining a second alongside it. A row that
     * already belongs to an installation is never re-attributed.</p>
     *
     * @param claimingInstallationId identifier of the installation claiming this row
     */
    public void attributeToInstallation(final UUID claimingInstallationId) {
        if (installationId == null) {
            installationId = claimingInstallationId;
        }
    }

    /**
     * Replaces the labels, so a device that has been renamed or upgraded stays recognisable.
     *
     * <p>Only meaningful once identity is the installation: while the names <em>were</em> the
     * identity, a changed name was by definition a different device.</p>
     *
     * @param newDeviceName  name of the machine as supplied by the client
     * @param newBrowserName browser derived from the user agent
     */
    public void relabel(final String newDeviceName, final String newBrowserName) {
        this.deviceName = newDeviceName;
        this.browserName = newBrowserName;
    }

    /**
     * Returns the account this device belongs to.
     *
     * @return the account
     */
    public UserEntity getUser() {
        return user;
    }

    /**
     * Returns the name of the machine.
     *
     * @return the device name
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Returns the browser this device signs in from.
     *
     * @return the browser name
     */
    public String getBrowserName() {
        return browserName;
    }

    /**
     * Returns when the device was last used.
     *
     * @return the last use moment
     */
    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    /**
     * Records that the device has just been used.
     *
     * @param lastUsedAt moment of use
     */
    public void markUsedAt(final Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
