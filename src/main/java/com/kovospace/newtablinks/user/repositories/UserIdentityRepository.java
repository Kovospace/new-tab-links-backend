package com.kovospace.newtablinks.user.repositories;

import com.kovospace.newtablinks.user.models.AuthenticationProviderType;
import com.kovospace.newtablinks.user.models.UserIdentityEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link UserIdentityEntity}.
 *
 * @since 0.0.2
 */
@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {

    /**
     * Finds the account behind an external identity.
     *
     * <p>This pair, and never the email address, is what recognises a returning user.</p>
     *
     * @param provider       provider that vouched for the identity
     * @param providerUserId immutable subject identifier issued by that provider
     * @return the matching identity, or an empty optional when it has never been linked
     */
    Optional<UserIdentityEntity> findByProviderAndProviderUserId(
            AuthenticationProviderType provider,
            String providerUserId);

    /**
     * Lists every external identity linked to a user.
     *
     * @param userId identifier of the owning user
     * @return the user's linked identities, empty when there are none
     */
    List<UserIdentityEntity> findAllByUserId(UUID userId);
}
