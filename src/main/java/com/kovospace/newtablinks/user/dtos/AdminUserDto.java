package com.kovospace.newtablinks.user.dtos;

import com.kovospace.newtablinks.entitlement.models.EntitlementSource;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A user as the operator sees them.
 *
 * <p>Everything {@link UserDto} carries, plus the two things only somebody repairing an account
 * needs: how many failed sign-ins have piled up against it, and whether that count has locked it.
 * A user has no business being told either about themselves, which is why this is a second shape
 * rather than more fields on the first.</p>
 *
 * <p>It also says whether the account is pro and, while it is, through what - so the operator
 * can tell an account that paid from one they gave pro to, knows which of the two the
 * premium checkbox can take back, and sees when it runs out.</p>
 *
 * <p>What is <strong>not</strong> here is the password hash. An operator can give an account a
 * new password; nobody needs to see the old one, and a hash on a screen is a hash in a log.</p>
 *
 * @param id                  identifier of the user
 * @param username            name the user signs in with
 * @param email               address identifying the user
 * @param displayName         name shown in the user interface
 * @param status              lifecycle state of the account
 * @param hasPassword         whether the account can be signed into with a password
 * @param failedLoginAttempts consecutive failed sign-ins since the last success
 * @param createdAt           when the user was created
 * @param updatedAt           when the user was last changed
 * @param premium             whether the account is pro right now; the same judgement as
 *                            {@code premium} on {@link UserDto}
 * @param premiumSource       where the pro entitlement came from while {@code premium} is true;
 *                            {@code null} whenever it is false
 * @param premiumUntil        when pro ends while {@code premium} is true - the end of a paid
 *                            period or of a one-year operator grant; {@code null} for lifetime
 *                            pro and whenever {@code premium} is false. Since 0.0.15.
 * @since 0.0.6
 */
@Schema(description = "A user account, as the operator sees it")
public record AdminUserDto(
        @Schema(description = "Identifier of the user") UUID id,
        @Schema(description = "Name the user signs in with", example = "kovo") String username,
        @Schema(description = "Address identifying the user", example = "someone@example.com") String email,
        @Schema(description = "Name shown in the user interface", example = "Matej") String displayName,
        @Schema(description = "Lifecycle state of the account") UserAccountStatus status,
        @Schema(description = "Whether the account has a password", example = "true") boolean hasPassword,
        @Schema(description = "Consecutive failed sign-ins", example = "0") int failedLoginAttempts,
        @Schema(description = "When the user was created") Instant createdAt,
        @Schema(description = "When the user was last changed") Instant updatedAt,
        @Schema(description = "Whether the account is pro right now", example = "true")
        boolean premium,
        @Schema(description = "Where the pro entitlement came from - paid (LIFETIME, "
                + "SUBSCRIPTION) or given by the operator (GRANT); null when not pro",
                example = "GRANT", nullable = true)
        EntitlementSource premiumSource,
        @Schema(description = "When pro ends while premium is true - the end of a paid period "
                + "or of a one-year operator grant; null for lifetime pro and when not pro",
                example = "2027-10-04T12:00:00Z", nullable = true)
        Instant premiumUntil) {
}
