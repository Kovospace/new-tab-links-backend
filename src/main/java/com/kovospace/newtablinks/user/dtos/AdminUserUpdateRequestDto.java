package com.kovospace.newtablinks.user.dtos;

import com.kovospace.newtablinks.user.models.UserAccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What an operator may change about an account.
 *
 * <p>The three account fields are required, because this replaces them: sending a partial body and having
 * the absent fields silently keep their old values is the kind of API that eventually wipes
 * somebody's display name by accident. The client shows the current values and sends them back.</p>
 *
 * <p><strong>The email address is changeable here and nowhere else.</strong> Users may not change
 * their own - that is a deliberate decision, not a gap, because an address is an identity and
 * letting it move quietly is how accounts get taken over. An operator repairing a typo somebody
 * made at registration is the one case that decision was never meant to block, and it is
 * deliberate, audited by the log line the service writes, and available to nobody else.</p>
 *
 * <p>The username is absent on purpose: it is what the account is known by, other people may have
 * seen it, and nothing about repairing an account needs it to move.</p>
 *
 * @param email       address identifying the user
 * @param displayName name shown in the user interface
 * @param status      lifecycle state to put the account in
 * @param premium     {@code true} to give pro through an operator grant, {@code false} to take
 *                    such a grant back, {@code null} or absent to leave pro as it is - so a
 *                    client that predates this field cannot revoke anything by omitting it.
 *                    Revoking pro that was paid for is refused.
 * @since 0.0.6
 */
@Schema(description = "The fields an operator may change on an account")
public record AdminUserUpdateRequestDto(

        @Schema(description = "Address identifying the user", example = "someone@example.com")
        @NotBlank @Email @Size(max = 320) String email,

        @Schema(description = "Name shown in the user interface", example = "Matej")
        @NotBlank @Size(max = 120) String displayName,

        @Schema(description = "Lifecycle state to put the account in")
        @NotNull UserAccountStatus status,

        @Schema(description = "true grants pro, false revokes an operator grant (a paid "
                + "entitlement is refused with 409), null or absent leaves pro unchanged",
                example = "true", nullable = true)
        Boolean premium) {
}
