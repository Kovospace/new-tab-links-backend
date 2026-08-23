package com.kovospace.newtablinks.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body accepted when the signed-in user edits their own profile.
 *
 * <p>Only the display name is editable here. Username and email address identify the account and
 * changing either has consequences - a new address must be proven before it replaces the old one -
 * so both belong to their own flows rather than to a general profile edit.</p>
 *
 * @param displayName name shown in the user interface
 * @since 0.0.2
 */
@Schema(description = "Body accepted when the signed-in user edits their own profile")
public record UserProfileUpdateRequestDto(

        @Schema(description = "Name shown in the user interface", example = "Matej")
        @NotBlank @Size(max = 120) String displayName) {
}
