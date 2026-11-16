package com.kovospace.newtablinks.fairuse.repositories;

import com.kovospace.newtablinks.fairuse.models.UserFairUseOverrideEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for the Fair Use limits raised per account, keyed by the account's identifier.
 *
 * @since 0.0.20
 */
public interface UserFairUseOverrideRepository
        extends JpaRepository<UserFairUseOverrideEntity, UUID> {

    /**
     * Returns which of the given accounts have an override, in one query.
     *
     * @param userIds identifiers of the accounts to ask about
     * @return the identifiers among them that have an override, in no particular order; empty
     *         when none has
     */
    @Query("SELECT override.userId FROM UserFairUseOverrideEntity override "
            + "WHERE override.userId IN :userIds")
    List<UUID> findUserIdsHavingOverrideAmong(@Param("userIds") Collection<UUID> userIds);
}
