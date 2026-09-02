package com.kovospace.newtablinks.auth.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kovospace.newtablinks.auth.utils.UsernameConstraints;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when registering an account the classic way, from the website.
 *
 * <p>The password is asked for twice. A mistyped password cannot be noticed by the person who
 * typed it - the field shows dots - and the account it creates cannot be signed into, so the
 * mistake surfaces later as a sign-in failure nobody can explain. The website asks twice and
 * checks before submitting; this checks again, because a client is not a place to enforce a
 * rule, and this endpoint is open to any client that ever registers a user.</p>
 *
 * @param username             name the user will sign in with
 * @param email                address the activation link is sent to
 * @param password             chosen password, in plaintext over TLS and never stored as given
 * @param passwordConfirmation the same password typed a second time
 * @param displayName          name shown in the user interface
 * @since 0.0.2
 */
@Schema(description = "Body accepted when registering an account with a password")
public record RegistrationRequestDto(

        @Schema(description = "Name the user will sign in with", example = "kovo")
        @NotBlank
        @Size(min = UsernameConstraints.MINIMUM_LENGTH, max = UsernameConstraints.MAXIMUM_LENGTH)
        @Pattern(regexp = UsernameConstraints.ALLOWED_CHARACTERS_PATTERN,
                message = UsernameConstraints.ALLOWED_CHARACTERS_MESSAGE)
        String username,

        @Schema(description = "Address the activation link is sent to", example = "someone@example.com")
        @NotBlank @Email @Size(max = 320) String email,

        @Schema(description = "Chosen password", example = "a long passphrase is best")
        @NotBlank @Size(min = 10, max = 200) String password,

        @Schema(description = "The same password typed a second time",
                example = "a long passphrase is best")
        @NotBlank String passwordConfirmation,

        @Schema(description = "Name shown in the user interface", example = "Matej")
        @NotBlank @Size(max = 120) String displayName) {

    /**
     * Whether the password was typed the same way twice.
     *
     * <p>A cross-field rule, so it belongs to the record rather than to either field. Reported
     * against a missing or blank value would be noise - {@code @NotBlank} on both fields already
     * says that - so a null on either side passes here and lets that constraint speak alone.</p>
     *
     * <p>Not a time-constant comparison, deliberately: both values arrived in the same request
     * from the same caller, so there is no secret here that a timing difference could disclose.
     * </p>
     *
     * <p>Hidden from the API schema and from serialization: it is a validation rule, not a field
     * anybody sends or receives, and a derived {@code passwordConfirmationMatching} property in
     * the published contract would only invite someone to try to set it.</p>
     *
     * @return {@code true} when the two match, or when either is absent
     */
    @AssertTrue(message = "The password confirmation must match the password")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isPasswordConfirmationMatching() {
        return password == null || passwordConfirmation == null
                || password.equals(passwordConfirmation);
    }
}
