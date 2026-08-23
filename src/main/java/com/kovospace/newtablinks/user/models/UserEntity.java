package com.kovospace.newtablinks.user.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Owner of everything this service stores.
 *
 * <p>Every environment, and therefore transitively every group, subgroup and link, belongs to
 * exactly one user. The user is the boundary of a synchronization: a client syncs one user's data
 * and never sees another's.</p>
 *
 * <p>An account can be reached in two ways, and both end up here:</p>
 * <ul>
 *   <li><strong>Classic registration</strong> - username, email and password, activated through a
 *       link sent by mail. Such an account starts {@link UserAccountStatus#PENDING_ACTIVATION}.</li>
 *   <li><strong>An external provider</strong> such as Google, recorded as a
 *       {@link UserIdentityEntity}. The provider has already proven the address, so the account is
 *       {@link UserAccountStatus#ACTIVE} immediately and {@link #getPasswordHash()} stays
 *       {@code null} - which is precisely why that field is nullable.</li>
 * </ul>
 *
 * @since 0.0.1
 */
@Entity
@Table(name = "app_user")
public class UserEntity extends AbstractAuditableEntity {

    /**
     * Name the user signs in with. Unique, and chosen at registration.
     */
    @Column(name = "username", nullable = false, unique = true, length = 60)
    private String username;

    /**
     * Address identifying the user, unique across the whole service.
     */
    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    /**
     * Hash of the sign-in password, or {@code null} for an account that only ever authenticates
     * through an external provider.
     *
     * <p>Never holds a plaintext password. The encoding scheme is chosen by the configured
     * {@link org.springframework.security.crypto.password.PasswordEncoder} and is recorded in the
     * value itself, so the scheme can be upgraded without a migration.</p>
     */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /**
     * Name shown in the user interface. Free text, not unique.
     */
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /**
     * Lifecycle state; only {@link UserAccountStatus#ACTIVE} may authenticate.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserAccountStatus status;

    /**
     * Number of consecutive failed sign-in attempts, reset on success.
     *
     * <p>The service has no rate limiting infrastructure, so this counter is what stands between
     * an account and an unlimited password guessing run.</p>
     */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    /**
     * Required by JPA.
     */
    protected UserEntity() {
    }

    /**
     * Creates a user.
     *
     * @param username     name the user signs in with
     * @param email        address identifying the user
     * @param passwordHash hash of the sign-in password, {@code null} for provider-only accounts
     * @param displayName  name shown in the user interface
     * @param status       lifecycle state the account starts in
     */
    public UserEntity(
            final String username,
            final String email,
            final String passwordHash,
            final String displayName,
            final UserAccountStatus status) {

        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = status;
        this.failedLoginAttempts = 0;
    }

    /**
     * Returns the name the user signs in with.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
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
     * Returns the stored password hash.
     *
     * @return the hash, or {@code null} when the account has no password
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Replaces the stored password hash.
     *
     * @param passwordHash the already encoded password, never plaintext
     */
    public void setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Tells whether the account can be signed into with a password at all.
     *
     * @return {@code true} when a password has been set
     */
    public boolean hasPassword() {
        return passwordHash != null;
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

    /**
     * Returns the lifecycle state of the account.
     *
     * @return the status
     */
    public UserAccountStatus getStatus() {
        return status;
    }

    /**
     * Replaces the lifecycle state of the account.
     *
     * @param status the status to set
     */
    public void setStatus(final UserAccountStatus status) {
        this.status = status;
    }

    /**
     * Tells whether the account is allowed to authenticate.
     *
     * @return {@code true} when the account is active
     */
    public boolean isActive() {
        return status == UserAccountStatus.ACTIVE;
    }

    /**
     * Returns the number of consecutive failed sign-in attempts.
     *
     * @return the failure count
     */
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    /**
     * Records one more consecutive failed sign-in attempt.
     */
    public void recordFailedLoginAttempt() {
        this.failedLoginAttempts++;
    }

    /**
     * Clears the consecutive failure counter after a successful sign-in.
     */
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
    }
}
