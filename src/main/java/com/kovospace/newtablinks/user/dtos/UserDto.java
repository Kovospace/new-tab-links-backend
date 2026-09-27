package com.kovospace.newtablinks.user.dtos;

import com.kovospace.newtablinks.user.models.UserAccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A user as returned to a client.
 *
 * @param id          identifier of the user
 * @param username    name the user signs in with
 * @param email       address identifying the user
 * @param displayName name shown in the user interface
 * @param status      lifecycle state of the account
 * @param hasPassword whether the account can be signed into with a password; {@code false} for an
 *                    account that only ever authenticates through an external provider, which is
 *                    what tells a website to offer "set a password" rather than "change password"
 * @param premium     whether the account is pro right now, judged by the server from its
 *                    entitlement at the moment of the response; {@code false} when it has none.
 *                    A client displays it and never derives it
 * @param createdAt   when the user was created
 * @param updatedAt   when the user was last changed
 * @since 0.0.1
 */
@Schema(description = "A user of the service")
public record UserDto(
        @Schema(description = "Identifier of the user") UUID id,
        @Schema(description = "Name the user signs in with", example = "kovo") String username,
        @Schema(description = "Address identifying the user", example = "someone@example.com") String email,
        @Schema(description = "Name shown in the user interface", example = "Matej") String displayName,
        @Schema(description = "Lifecycle state of the account") UserAccountStatus status,
        @Schema(description = "Whether the account has a password", example = "true") boolean hasPassword,
        @Schema(description = "Whether the account is pro right now. Decided by the server from "
                + "the account's entitlement when the response is built - a cancelled or past-due "
                + "subscription stays pro until the end of what was paid for, a refunded or expired "
                + "one does not. False when the account never bought anything. Never derive it on "
                + "the client.", example = "false") boolean premium,
        @Schema(description = "When the user was created") Instant createdAt,
        @Schema(description = "When the user was last changed") Instant updatedAt) {
}
