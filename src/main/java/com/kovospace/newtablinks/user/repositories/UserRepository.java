package com.kovospace.newtablinks.user.repositories;

import com.kovospace.newtablinks.user.models.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * Finds a user by the name they sign in with.
     *
     * @param username name to look up, compared exactly
     * @return the matching user, or an empty optional when nobody uses that name
     */
    Optional<UserEntity> findByUsername(String username);

    /**
     * Finds a user by either of the two things they may type into a sign-in form.
     *
     * <p>Both columns are unique and neither format overlaps the other in practice, so a single
     * lookup can accept whichever the user typed without asking which one it is.</p>
     *
     * @param usernameOrEmail the value typed into the sign-in form
     * @return the matching user, or an empty optional when nothing matches
     */
    @Query("select appUser from UserEntity appUser "
            + "where appUser.username = :usernameOrEmail or appUser.email = :usernameOrEmail")
    Optional<UserEntity> findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);

    /**
     * Tells whether a user with the given address already exists.
     *
     * @param email address to look up, compared exactly
     * @return {@code true} when the address is already taken
     */
    boolean existsByEmail(String email);

    /**
     * Tells whether the given sign-in name is already taken.
     *
     * @param username name to look up, compared exactly
     * @return {@code true} when the name is already taken
     */
    boolean existsByUsername(String username);
}
