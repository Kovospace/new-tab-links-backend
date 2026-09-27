package com.kovospace.newtablinks.user.dtos;

import com.kovospace.newtablinks.auth.utils.UsernameConstraints;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An account created by the operator rather than by somebody registering.
 *
 * <p>For testing, and for the occasional account that has to exist before its owner can make it.
 * It skips everything registration does around the account itself - no activation mail is sent,
 * no uniform answer is given, and the status is whatever the operator asks for - so an
 * {@code ACTIVE} account created here can be signed into immediately.</p>
 *
 * <p>The password is optional. Left out, the account has none, which is exactly the shape of an
 * account that only ever signs in through Google, and the owner can set one through the password
 * reset flow.</p>
 *
 * @param username    name the user will sign in with
 * @param email       address identifying the user
 * @param displayName name shown in the user interface
 * @param password    password to set, or absent for an account without one
 * @param status      lifecycle state to create the account in
 * @param premium     whether to give the account pro through an operator grant; absent means
 *                    {@code false}
 * @since 0.0.6
 */
@Schema(description = "An account created by the operator")
public record AdminUserCreateRequestDto(

        @Schema(description = "Name the user will sign in with", example = "kovo")
        @NotBlank
        @Size(min = UsernameConstraints.MINIMUM_LENGTH, max = UsernameConstraints.MAXIMUM_LENGTH)
        @Pattern(regexp = UsernameConstraints.ALLOWED_CHARACTERS_PATTERN,
                message = UsernameConstraints.ALLOWED_CHARACTERS_MESSAGE)
        String username,

        @Schema(description = "Address identifying the user", example = "someone@example.com")
        @NotBlank @Email @Size(max = 320) String email,

        @Schema(description = "Name shown in the user interface", example = "Matej")
        @NotBlank @Size(max = 120) String displayName,

        @Schema(description = "Password to set, or absent for an account without one")
        @Size(min = 10, max = 200) String password,

        @Schema(description = "Lifecycle state to create the account in")
        @NotNull UserAccountStatus status,

        @Schema(description = "Whether to give the account pro through an operator grant; "
                + "absent means false", example = "false")
        boolean premium) {
}
