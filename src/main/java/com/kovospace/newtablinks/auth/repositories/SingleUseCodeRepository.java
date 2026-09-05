package com.kovospace.newtablinks.auth.repositories;

import com.kovospace.newtablinks.auth.models.SingleUseCodeEntity;
import com.kovospace.newtablinks.auth.models.SingleUseCodePurpose;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link SingleUseCodeEntity}.
 *
 * @since 0.0.2
 */
@Repository
public interface SingleUseCodeRepository extends JpaRepository<SingleUseCodeEntity, UUID> {

    /**
     * Finds a code by its hash, within one purpose.
     *
     * <p>The purpose is part of the lookup so that a code minted for one exchange cannot be
     * redeemed at the endpoint serving the other.</p>
     *
     * @param codeHash hash of the code the caller presented
     * @param purpose  exchange the caller is attempting
     * @return the matching code, or an empty optional when there is none
     */
    Optional<SingleUseCodeEntity> findByCodeHashAndPurpose(String codeHash, SingleUseCodePurpose purpose);

    /**
     * Lists every single use code minted for a user, redeemed ones included.
     *
     * @param userId identifier of the owning user
     * @return the user's codes, empty when there are none
     */
    List<SingleUseCodeEntity> findAllByUserId(UUID userId);
}
