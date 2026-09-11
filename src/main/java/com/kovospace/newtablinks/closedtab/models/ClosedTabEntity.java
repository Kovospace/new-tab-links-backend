package com.kovospace.newtablinks.closedtab.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A tab the user closed, kept so that it can be found again from any of their devices.
 *
 * <p><strong>This is a log, not user data.</strong> Every other entity here is something the
 * user made and would miss; a closed tab is something that happened. The difference shows in
 * three places: the record has no display position, because its order is when it happened; it is
 * never edited, only inserted and removed; and the browser extension caps the list per profile
 * and pushes what it drops as deletes, so this is by far the highest-churn kind the account
 * holds.</p>
 *
 * <p>It belongs to a profile directly, not to an environment or a group. A closed tab is not
 * filed anywhere - the panel listing them belongs to the profile, in the same way its settings
 * do.</p>
 *
 * @since 0.0.8
 */
@Entity
@Table(name = "closed_tab")
public class ClosedTabEntity extends AbstractAuditableEntity {

    /**
     * Profile whose list this tab is on. Always present.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    /**
     * Address the tab was showing.
     */
    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    /**
     * What the page called itself, empty when it never said.
     *
     * <p>Empty rather than {@code null}: a page with no title is an ordinary case here, and the
     * extension models the field as a plain string it always has.</p>
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * Address of the favicon the browser had for the page, or {@code null} when it had none.
     */
    @Column(name = "favicon_url", length = 2048)
    private String faviconUrl;

    /**
     * Moment the tab was closed, as reported by the device that closed it.
     *
     * <p>Deliberately not a server timestamp, and deliberately not derived from
     * {@link #getCreatedAt()}. The device knows when the tab was closed; the row may reach the
     * server minutes or hours later, and the list is ordered by when it happened.</p>
     */
    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    /**
     * Name of the device the tab was closed on, or {@code null} when it has none.
     */
    @Column(name = "device_name", length = 120)
    private String deviceName;

    /**
     * Required by JPA.
     */
    protected ClosedTabEntity() {
    }

    /**
     * Creates a closed tab.
     *
     * @param profile    profile whose list the tab is on
     * @param url        address the tab was showing
     * @param title      what the page called itself, empty when it never said
     * @param faviconUrl address of the favicon the browser had, may be {@code null}
     * @param closedAt   moment the tab was closed, as reported by the closing device
     * @param deviceName name of the device the tab was closed on, may be {@code null}
     */
    public ClosedTabEntity(
            final ProfileEntity profile,
            final String url,
            final String title,
            final String faviconUrl,
            final Instant closedAt,
            final String deviceName) {

        this.profile = profile;
        this.url = url;
        this.title = title;
        this.faviconUrl = faviconUrl;
        this.closedAt = closedAt;
        this.deviceName = deviceName;
    }

    /**
     * Returns the profile whose list this tab is on.
     *
     * @return the profile, never {@code null}
     */
    public ProfileEntity getProfile() {
        return profile;
    }

    /**
     * Moves the tab onto another profile's list.
     *
     * @param profile the profile the tab belongs to
     */
    public void setProfile(final ProfileEntity profile) {
        this.profile = profile;
    }

    /**
     * Returns the address the tab was showing.
     *
     * @return the URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Replaces the address the tab was showing.
     *
     * @param url the URL to set
     */
    public void setUrl(final String url) {
        this.url = url;
    }

    /**
     * Returns what the page called itself.
     *
     * @return the title, empty when the page never said
     */
    public String getTitle() {
        return title;
    }

    /**
     * Replaces what the page called itself.
     *
     * @param title the title to set, empty when the page has none
     */
    public void setTitle(final String title) {
        this.title = title;
    }

    /**
     * Returns the address of the favicon the browser had for the page.
     *
     * @return the favicon URL, or {@code null} when there was none
     */
    public String getFaviconUrl() {
        return faviconUrl;
    }

    /**
     * Replaces the address of the favicon the browser had for the page.
     *
     * @param faviconUrl the favicon URL to set, may be {@code null}
     */
    public void setFaviconUrl(final String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    /**
     * Returns the moment the tab was closed.
     *
     * @return the closing moment as the closing device reported it, never {@code null} on a
     *         stored row
     */
    public Instant getClosedAt() {
        return closedAt;
    }

    /**
     * Replaces the moment the tab was closed.
     *
     * <p>Callers pass what the client sent. Nothing in this application substitutes a server
     * clock here; a row whose client did not supply one is refused rather than stamped.</p>
     *
     * @param closedAt the closing moment to set
     */
    public void setClosedAt(final Instant closedAt) {
        this.closedAt = closedAt;
    }

    /**
     * Returns the name of the device the tab was closed on.
     *
     * @return the device name, or {@code null} when that device has none
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Replaces the name of the device the tab was closed on.
     *
     * @param deviceName the device name to set, may be {@code null}
     */
    public void setDeviceName(final String deviceName) {
        this.deviceName = deviceName;
    }
}
