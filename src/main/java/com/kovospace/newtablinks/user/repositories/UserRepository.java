package com.kovospace.newtablinks.user.repositories;

import com.kovospace.newtablinks.user.models.UserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * Finds the accounts whose username, email or display name contains the given text.
     *
     * <p>For the operator's account list, which is a search box over a table. Case-insensitive
     * and unanchored, because somebody looking for an account has a fragment of one of the three
     * and does not know which.</p>
     *
     * <p>Blank text is not a special case here - the caller passes an empty string and every
     * account matches, which is exactly the unfiltered list.</p>
     *
     * @param searchText text to look for; an empty string matches everything
     * @param pageable   which page to return and how to order it
     * @return the matching page of accounts
     */
    @Query("""
            SELECT user FROM UserEntity user
            WHERE LOWER(user.username) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(user.email) LIKE LOWER(CONCAT('%', :searchText, '%'))
               OR LOWER(user.displayName) LIKE LOWER(CONCAT('%', :searchText, '%'))
            """)
    Page<UserEntity> searchAccounts(@Param("searchText") String searchText, Pageable pageable);

    /**
     * Loads an account and locks its row until the transaction ends.
     *
     * <p>The serialization point of every Fair Use Policy check: a write that counts an
     * account's records and then adds one takes this lock first, so two parallel requests at one
     * below a cap cannot both see room. The account row is used because every account has one -
     * unlike the entitlement row, which a free account does not.</p>
     *
     * @param userId identifier of the account
     * @return the account, or empty when it does not exist
     * @since 0.0.16
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appUser from UserEntity appUser where appUser.id = :userId")
    Optional<UserEntity> findByIdForUpdate(@Param("userId") UUID userId);
}
