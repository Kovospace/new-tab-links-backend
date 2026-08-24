package com.kovospace.newtablinks.user.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * One place a user has signed in from: a machine and a browser on it.
 *
 * <p>The same physical machine running three browsers is three devices, because that is what the
 * user actually experiences - each browser holds its own tokens and is signed out separately.
 * The pair {@code (deviceName, browserName)} is therefore the natural key within one account,
 * and signing in again from the same pair updates this row rather than adding another.</p>
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
 * @since 0.0.3
 */
@Entity
@Table(
        name = "user_device",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_device_identity",
                columnNames = {"user_id", "device_name", "browser_name"}))
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
     * @param user        account the device belongs to
     * @param deviceName  name of the machine as supplied by the client
     * @param browserName browser derived from the user agent
     * @param lastUsedAt  moment of this first use
     */
    public UserDeviceEntity(
            final UserEntity user,
            final String deviceName,
            final String browserName,
            final Instant lastUsedAt) {

        this.user = user;
        this.deviceName = deviceName;
        this.browserName = browserName;
        this.lastUsedAt = lastUsedAt;
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
