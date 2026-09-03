package com.kovospace.newtablinks.auth.dtos;

import com.kovospace.newtablinks.auth.models.AccountEmailDeliveryOutcome;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Deliberately uninformative answer to a registration attempt.
 *
 * <p>The same body is returned whether an account was created or the address was already taken,
 * so that registration cannot be used to discover who is registered. The difference is in what
 * gets mailed to the address, which only its owner can read.</p>
 *
 * <p>{@code emailDelivered} does not weaken that. Both of those branches send a message - one an
 * activation link, the other a notice to the existing owner - so whether the relay accepted it
 * says nothing about which branch ran. An endpoint whose branches do <em>not</em> both send, such
 * as resending an activation link to an address that may not exist, must leave the field unset
 * rather than report a value that would give the difference away.</p>
 *
 * @param message        wording to show the user
 * @param emailDelivered false when the message could not be handed to the mail relay, true when it
 *                       could, and null when this endpoint does not report delivery at all
 * @since 0.0.2
 */
@Schema(description = "Uniform answer to a registration attempt")
public record RegistrationAcceptedDto(

        @Schema(description = "Wording to show the user",
                example = "If that address can receive mail, an activation link is on its way.")
        String message,

        @Schema(description = "Whether the message this attempt triggered reached the mail relay. "
                + "False means nothing was sent and the address will receive nothing, so the "
                + "caller should be told to try again shortly rather than to wait. Null means "
                + "this endpoint does not report delivery. It never reveals whether the address "
                + "was already registered: both outcomes send a message.",
                example = "true", nullable = true)
        Boolean emailDelivered) {

    /**
     * Builds an answer that reports how far the message got.
     *
     * @param message wording to show the user
     * @param outcome what became of the send this attempt triggered
     * @return the answer, with delivery reported
     * @since 0.0.5
     */
    public static RegistrationAcceptedDto reportingDelivery(
            final String message, final AccountEmailDeliveryOutcome outcome) {

        return new RegistrationAcceptedDto(message, !outcome.isFailure());
    }

    /**
     * Builds an answer that says nothing about delivery.
     *
     * <p>For the endpoints where reporting it would disclose whether an address is registered,
     * because only one of their branches sends anything at all.</p>
     *
     * @param message wording to show the user
     * @return the answer, with delivery left unreported
     * @since 0.0.5
     */
    public static RegistrationAcceptedDto withoutDeliveryReport(final String message) {
        return new RegistrationAcceptedDto(message, null);
    }
}
