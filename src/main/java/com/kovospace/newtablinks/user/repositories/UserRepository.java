package com.kovospace.newtablinks.user.repositories;

import com.kovospace.newtablinks.user.models.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link UserEntity}.
 *
 * @since 0.0.1
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Finds a user by the address identifying them.
     *
     * @param email address to look up, compared exactly
     * @return the matching user, or an empty optional when no user has that address
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * Tells whether a user with the given address already exists.
     *
     * @param email address to look up, compared exactly
     * @return {@code true} when the address is already taken
     */
    boolean existsByEmail(String email);
}
