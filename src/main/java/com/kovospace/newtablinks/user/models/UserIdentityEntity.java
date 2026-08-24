package com.kovospace.newtablinks.user.models;

import com.kovospace.newtablinks.common.models.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A link between a local account and an external identity provider.
 *
 * <p>One user may hold several identities - signing in with Google today and with something else
 * later - so this is a separate table rather than columns on the user.</p>
 *
 * <p><strong>The identity is {@code (provider, providerUserId)}, never the email address.</strong>
 * Addresses change, get reassigned on corporate domains, and some providers do not return one at
 * all. Google's stable key is the {@code sub} claim; {@link #getEmailAtProvider()} is kept only
 * as a diagnostic and must not be used to match users.</p>
 *
 * @since 0.0.2
 */
@Entity
@Table(
        name = "user_identity",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_identity_provider_subject",
                columnNames = {"provider", "provider_user_id"}))
public class UserIdentityEntity extends AbstractAuditableEntity {

    /**
     * Account this external identity belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * Provider that vouches for this identity.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 40)
    private AuthenticationProviderType provider;

    /**
     * Immutable subject identifier issued by the provider.
     */
    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    /**
     * Address the provider reported at the time of linking. Diagnostic only.
     */
    @Column(name = "email_at_provider", length = 320)
    private String emailAtProvider;

    /**
     * Required by JPA.
     */
    protected UserIdentityEntity() {
    }

    /**
     * Links an external identity to an account.
     *
     * @param user            account the identity belongs to
     * @param provider        provider that vouches for the identity
     * @param providerUserId  immutable subject identifier issued by the provider
     * @param emailAtProvider address reported by the provider, may be {@code null}
     */
    public UserIdentityEntity(
            final UserEntity user,
            final AuthenticationProviderType provider,
            final String providerUserId,
            final String emailAtProvider) {

        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.emailAtProvider = emailAtProvider;
    }

    /**
     * Returns the account this identity belongs to.
     *
     * @return the account
     */
    public UserEntity getUser() {
        return user;
    }

    /**
     * Returns the provider that vouches for this identity.
     *
     * @return the provider
     */
    public AuthenticationProviderType getProvider() {
        return provider;
    }

    /**
     * Returns the immutable subject identifier issued by the provider.
     *
     * @return the provider side identifier
     */
    public String getProviderUserId() {
        return providerUserId;
    }

    /**
     * Returns the address the provider reported when the identity was linked.
     *
     * @return the address, or {@code null} when the provider reported none
     */
    public String getEmailAtProvider() {
        return emailAtProvider;
    }

    /**
     * Records the address most recently reported by the provider.
     *
     * @param emailAtProvider the address to store, may be {@code null}
     */
    public void setEmailAtProvider(final String emailAtProvider) {
        this.emailAtProvider = emailAtProvider;
    }
}
