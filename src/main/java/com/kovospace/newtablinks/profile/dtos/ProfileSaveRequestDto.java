package com.kovospace.newtablinks.profile.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body accepted when creating or replacing a profile.
 *
 * <p>The owner is deliberately absent: it is taken from the access token, because an owner
 * supplied in the body would be a request to act as whoever the caller names.</p>
 *
 * @param name name shown on the profile switcher
 * @param enableDragAndDrop whether the profile lets its links and groups be rearranged by dragging
 * @param hideTips whether the profile hides the tips shown on the new tab page background
 * @param dismissedTips identifiers of the tips the profile has dismissed one by one; absent is none
 * @since 0.0.6
 */
@Schema(description = "Body accepted when creating or replacing a profile")
public record ProfileSaveRequestDto(

        @Schema(description = "Name shown on the profile switcher", example = "Default")
        @NotBlank @Size(max = 120) String name,

        @Schema(description = "Whether the profile lets its links and groups be rearranged by "
                + "dragging", example = "false")
        boolean enableDragAndDrop,

        @Schema(description = "Whether the profile hides the tips shown on the new tab page "
                + "background", example = "false")
        boolean hideTips,

        @Schema(description = "Identifiers of the new tab page tips the profile has dismissed "
                + "one by one; absent means none", example = "[\"hide-tips\"]")
        @Size(max = 100) List<@NotBlank @Size(max = 64) String> dismissedTips) {
}
