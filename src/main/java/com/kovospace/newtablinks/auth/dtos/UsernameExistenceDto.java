package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Answer to "is this username already registered?".
 *
 * <p>Deliberately one boolean and nothing else. The question is asked while somebody types, so
 * the answer must stay cheap, and anything more would disclose more than the registration form
 * needs.</p>
 *
 * @param exists whether an account already uses the queried name
 * @since 0.0.4
 */
@Schema(description = "Whether a username is already registered")
public record UsernameExistenceDto(

        @Schema(description = "True when an account already uses that username", example = "true")
        boolean exists) {
}
