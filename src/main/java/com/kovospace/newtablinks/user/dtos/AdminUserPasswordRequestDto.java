package com.kovospace.newtablinks.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * A password an operator sets on somebody's account.
 *
 * <p>For the account whose owner cannot get back in at all - no working address, no reachable
 * reset link. The normal path is the reset mail, and it should be tried first: this one hands a
 * password to whoever asked the operator for it, and the account's owner is not told.</p>
 *
 * <p>An absent or blank password removes it, leaving an account that can only sign in through an
 * external provider. That is a real shape - accounts created through Google have always looked
 * like that - and not an error.</p>
 *
 * @param password the password to set, or absent to leave the account without one
 * @since 0.0.6
 */
@Schema(description = "A password set on an account by the operator")
public record AdminUserPasswordRequestDto(

        @Schema(description = "The password to set, or absent to remove it")
        @Size(min = 10, max = 200) String password) {
}
