package com.kovospace.newtablinks.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A freshly issued visitor token, together with the rules it will be judged by.
 *
 * <p>The limits travel with the token on purpose. The website has to pace itself to stay inside
 * them, and the only alternative - configuring the same numbers a second time in the browser -
 * guarantees that one day the two will disagree and real visitors will be refused. Publishing
 * them costs nothing: a limit is not enforced by being secret, and the caller who intends to
 * exceed it learns it from one rejected request anyway.</p>
 *
 * @param token                              the value to send in {@code X-Visitor-Token}
 * @param expiresAt                          moment the token stops working
 * @param minimumFirstUseDelayMilliseconds   how long to wait before the first call
 * @param minimumRequestIntervalMilliseconds shortest gap allowed between two calls
 * @param maximumUses                        how many calls this token may make in total
 * @since 0.0.5
 */
@Schema(description = "A metered pass for the endpoints an anonymous visitor may call")
public record VisitorTokenDto(

        @Schema(description = "The value to send in the X-Visitor-Token header")
        String token,

        @Schema(description = "Moment the token stops working")
        Instant expiresAt,

        @Schema(description = "How long to wait after issue before the first call", example = "500")
        long minimumFirstUseDelayMilliseconds,

        @Schema(description = "Shortest gap allowed between two calls", example = "200")
        long minimumRequestIntervalMilliseconds,

        @Schema(description = "How many calls this token may make in total", example = "250")
        int maximumUses) {
}
