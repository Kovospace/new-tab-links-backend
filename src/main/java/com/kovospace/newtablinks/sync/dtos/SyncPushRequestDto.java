package com.kovospace.newtablinks.sync.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A batch of changes one device made, in the order it made them.
 *
 * <p>The owner is deliberately absent, as it is on every other request body in this application:
 * it comes from the access token, because an owner supplied in the body would be a request to act
 * as whoever the caller names.</p>
 *
 * @param originDeviceId identifier of the installation that made these changes, echoed back on
 *                       the refresh notification so that installation can ignore its own change;
 *                       may be {@code null} from a client that does not track one
 * @param operations     the changes, applied strictly in this order
 * @since 0.0.6
 */
@Schema(description = "A batch of changes one device made, in the order it made them")
public record SyncPushRequestDto(

        @Schema(description = "Installation that made these changes, so it can ignore the echo",
                example = "1f0f2b4c-6d5e-4a3b-9c8d-7e6f5a4b3c2d")
        @Size(max = 120) String originDeviceId,

        @Schema(description = "The changes, applied strictly in this order")
        @NotNull @Valid List<SyncOperationDto> operations) {
}
