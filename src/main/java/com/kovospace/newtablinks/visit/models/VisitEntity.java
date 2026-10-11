package com.kovospace.newtablinks.visit.models;

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
 * A page the user visited, kept so that their browsing history can be read on any of their
 * devices.
 *
 * <p>Shaped exactly like {@link com.kovospace.newtablinks.closedtab.models.ClosedTabEntity}, and
 * for the same reason: <strong>this is a log, not user data.</strong> It has no display position,
 * because its order is when it happened; it is never edited in any meaningful way, only inserted
 * and removed; and the browser extension keeps a rolling list per profile and pushes what it
 * drops as deletes.</p>
 *
 * <p>Nothing here is unique by address. The extension keeps at most one visit per page per local
 * day per device, and the same page visited on two days, or on two devices, is two visits.</p>
 *
 * <p>Recorded only while the profile's {@code recordBrowsingHistory} setting is on, which is the
 * extension's decision: this server stores what it is sent and deletes what it is told to.</p>
 *
 * @since 0.0.19
 */
@Entity
@Table(name = "visit")
public class VisitEntity extends AbstractAuditableEntity {

    /**
     * Profile whose history this visit is in. Always present.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    /**
     * Address of the page that was visited.
     */
    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    /**
     * What the page called itself, empty when it never said.
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * Address of the favicon the browser had for the page, or {@code null} when it had none.
     */
    @Column(name = "favicon_url", length = 2048)
    private String faviconUrl;

    /**
     * Moment the page was visited, as reported by the device that visited it.
     *
     * <p>Never a server clock, for the reason given on
     * {@link com.kovospace.newtablinks.closedtab.models.ClosedTabEntity#getClosedAt()}.</p>
     */
    @Column(name = "visited_at", nullable = false)
    private Instant visitedAt;

    /**
     * Name of the device the page was visited on, or {@code null} when it has none.
     */
    @Column(name = "device_name", length = 120)
    private String deviceName;

    /**
     * Required by JPA.
     */
    protected VisitEntity() {
    }

    /**
     * Creates a visit.
     *
     * @param profile    profile whose history the visit is in
     * @param url        address of the page that was visited
     * @param title      what the page called itself, empty when it never said
     * @param faviconUrl address of the favicon the browser had, may be {@code null}
     * @param visitedAt  moment the page was visited, as reported by the visiting device
     * @param deviceName name of the device the page was visited on, may be {@code null}
     */
    public VisitEntity(
            final ProfileEntity profile,
            final String url,
            final String title,
            final String faviconUrl,
            final Instant visitedAt,
            final String deviceName) {

        this.profile = profile;
        this.url = url;
        this.title = title;
        this.faviconUrl = faviconUrl;
        this.visitedAt = visitedAt;
        this.deviceName = deviceName;
    }

    /**
     * Returns the profile whose history this visit is in.
     *
     * @return the profile, never {@code null}
     */
    public ProfileEntity getProfile() {
        return profile;
    }

    /**
     * Moves the visit into another profile's history.
     *
     * @param profile the profile the visit belongs to
     */
    public void setProfile(final ProfileEntity profile) {
        this.profile = profile;
    }

    /**
     * Returns the address of the page that was visited.
     *
     * @return the URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Replaces the address of the page that was visited.
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
     * Returns the moment the page was visited.
     *
     * @return the visiting moment as the visiting device reported it, never {@code null} on a
     *         stored row
     */
    public Instant getVisitedAt() {
        return visitedAt;
    }

    /**
     * Replaces the moment the page was visited.
     *
     * <p>Callers pass what the client sent. Nothing in this application substitutes a server
     * clock here; a row whose client did not supply one is refused rather than stamped.</p>
     *
     * @param visitedAt the visiting moment to set
     */
    public void setVisitedAt(final Instant visitedAt) {
        this.visitedAt = visitedAt;
    }

    /**
     * Returns the name of the device the page was visited on.
     *
     * @return the device name, or {@code null} when that device has none
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Replaces the name of the device the page was visited on.
     *
     * @param deviceName the device name to set, may be {@code null}
     */
    public void setDeviceName(final String deviceName) {
        this.deviceName = deviceName;
    }
}
