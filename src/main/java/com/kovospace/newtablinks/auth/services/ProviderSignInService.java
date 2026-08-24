package com.kovospace.newtablinks.auth.services;

import com.kovospace.newtablinks.user.models.AuthenticationProviderType;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.models.UserEntity;
import com.kovospace.newtablinks.user.models.UserIdentityEntity;
import com.kovospace.newtablinks.user.repositories.UserIdentityRepository;
import com.kovospace.newtablinks.user.repositories.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a successful provider sign-in into a local account.
 *
 * <p>Three cases, resolved in this order:</p>
 * <ol>
 *   <li>The provider identity is already linked - that is the account, and nothing else is
 *       consulted.</li>
 *   <li>The address the provider reports belongs to an existing local account - the identity is
 *       linked to it, so signing up classically and later using Google does not silently create a
 *       second account.</li>
 *   <li>Neither - a new account is created, already {@link UserAccountStatus#ACTIVE} because the
 *       provider has proven the address, and with no password at all.</li>
 * </ol>
 *
 * <p>Case 2 is worth being deliberate about: linking on a provider-verified address is convenient
 * and safe, but linking on an <em>unverified</em> one would let anyone who can claim an address at
 * a provider take over the matching local account. Only a verified address may link.</p>
 *
 * @since 0.0.2
 */
@Service
public class ProviderSignInService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderSignInService.class);

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    /**
     * Creates the service.
     *
     * @param userRepository         stores accounts
     * @param userIdentityRepository stores provider links
     */
    public ProviderSignInService(
            final UserRepository userRepository,
            final UserIdentityRepository userIdentityRepository) {

        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
    }

    /**
     * Finds or creates the account behind a provider sign-in.
     *
     * @param provider        provider that vouched for the user
     * @param providerUserId  immutable subject identifier from the provider
     * @param emailAddress    address reported by the provider, may be {@code null}
     * @param emailVerified   whether the provider states it has verified that address
     * @param displayName     name reported by the provider, may be {@code null}
     * @return the account to sign in as
     */
    @Transactional
    public UserEntity resolveAccountForProviderSignIn(
            final AuthenticationProviderType provider,
            final String providerUserId,
            final String emailAddress,
            final boolean emailVerified,
            final String displayName) {

        final Optional<UserIdentityEntity> existingIdentity =
                userIdentityRepository.findByProviderAndProviderUserId(provider, providerUserId);

        if (existingIdentity.isPresent()) {
            final UserIdentityEntity identity = existingIdentity.get();
            identity.setEmailAtProvider(emailAddress);
            return identity.getUser();
        }

        final String normalisedEmail = emailAddress == null
                ? null
                : emailAddress.trim().toLowerCase(Locale.ROOT);

        if (normalisedEmail != null && emailVerified) {
            final Optional<UserEntity> accountWithSameAddress =
                    userRepository.findByEmail(normalisedEmail);

            if (accountWithSameAddress.isPresent()) {
                final UserEntity account = accountWithSameAddress.get();
                linkIdentityTo(account, provider, providerUserId, normalisedEmail);
                LOGGER.info("Linked {} identity to existing account {}", provider, account.getId());
                return account;
            }
        }

        return createAccountFor(provider, providerUserId, normalisedEmail, displayName);
    }

    /**
     * Creates a brand new account for a provider sign-in.
     *
     * @param provider       provider that vouched for the user
     * @param providerUserId immutable subject identifier from the provider
     * @param emailAddress   normalised address, may be {@code null}
     * @param displayName    name reported by the provider, may be {@code null}
     * @return the created account
     */
    private UserEntity createAccountFor(
            final AuthenticationProviderType provider,
            final String providerUserId,
            final String emailAddress,
            final String displayName) {

        final UserEntity account = userRepository.save(new UserEntity(
                deriveAvailableUsername(emailAddress, provider, providerUserId),
                emailAddress != null
                        ? emailAddress
                        : synthesiseAddressPlaceholder(provider, providerUserId),
                null,
                displayName != null && !displayName.isBlank() ? displayName : "NewTabLinks user",
                UserAccountStatus.ACTIVE));

        linkIdentityTo(account, provider, providerUserId, emailAddress);
        LOGGER.info("Created account {} from a {} sign-in", account.getId(), provider);
        return account;
    }

    /**
     * Attaches a provider identity to an account.
     *
     * @param account        account to attach to
     * @param provider       provider that vouched for the identity
     * @param providerUserId immutable subject identifier from the provider
     * @param emailAddress   address reported by the provider, may be {@code null}
     */
    private void linkIdentityTo(
            final UserEntity account,
            final AuthenticationProviderType provider,
            final String providerUserId,
            final String emailAddress) {

        userIdentityRepository.save(
                new UserIdentityEntity(account, provider, providerUserId, emailAddress));
    }

    /**
     * Picks a free username for an account created from a provider sign-in.
     *
     * <p>Derived from the local part of the address, then suffixed until free. The user never
     * chose it and may never see it - they sign in through the provider or a connect code - so it
     * only has to be unique and inoffensive.</p>
     *
     * @param emailAddress   normalised address, may be {@code null}
     * @param provider       provider, used when there is no address to derive from
     * @param providerUserId subject identifier, used as the last resort
     * @return a username not currently in use
     */
    private String deriveAvailableUsername(
            final String emailAddress,
            final AuthenticationProviderType provider,
            final String providerUserId) {

        final String base = emailAddress != null
                ? sanitiseForUsername(emailAddress.substring(0, emailAddress.indexOf('@')))
                : sanitiseForUsername(provider.name() + "-" + providerUserId);

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    /**
     * Reduces arbitrary text to the character set a username allows.
     *
     * @param rawText text to reduce
     * @return a usable username fragment, never empty
     */
    private static String sanitiseForUsername(final String rawText) {
        final String sanitised = rawText.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        final String trimmed = sanitised.length() > 50 ? sanitised.substring(0, 50) : sanitised;
        return trimmed.length() >= 3 ? trimmed : "user" + trimmed;
    }

    /**
     * Produces a placeholder address for a provider that returned none.
     *
     * <p>The email column is unique and not null because every ordinary account has an address;
     * a provider that declines to share one - which Facebook does routinely - would otherwise be
     * unable to create an account at all. The placeholder is unroutable by construction, so it
     * can never receive mail or collide with a real address.</p>
     *
     * @param provider       provider that returned no address
     * @param providerUserId subject identifier, unique within that provider
     * @return a synthetic, unroutable address
     */
    private static String synthesiseAddressPlaceholder(
            final AuthenticationProviderType provider,
            final String providerUserId) {

        return "%s-%s@no-address.invalid".formatted(
                provider.name().toLowerCase(Locale.ROOT), providerUserId);
    }
}
