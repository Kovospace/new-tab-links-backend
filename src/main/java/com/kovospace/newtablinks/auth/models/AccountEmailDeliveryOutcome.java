package com.kovospace.newtablinks.auth.models;

/**
 * What became of one attempt to send an account message.
 *
 * <p>This says only how far the message got from here. {@link #SENT} means the relay accepted it,
 * which is the last thing this application can observe; whether it then reached an inbox, a spam
 * folder or a bounce is the relay's business and never comes back through this enum.</p>
 *
 * @since 0.0.5
 */
public enum AccountEmailDeliveryOutcome {

    /** The relay accepted the message. */
    SENT,

    /**
     * Nothing was transmitted because {@code newtablinks.mail.enabled} is off; the message went to
     * the log instead, activation link included.
     */
    SUPPRESSED,

    /** The relay could not be reached, or refused the message. */
    FAILED;

    /**
     * Whether this outcome is worth telling the person who triggered the send about.
     *
     * <p>{@link #SUPPRESSED} is deliberately not a failure. A deployment with mail switched off is
     * configured that way on purpose - typically a developer reading links out of the log - and
     * warning them that mail is broken would be wrong.</p>
     *
     * @return true only for {@link #FAILED}
     */
    public boolean isFailure() {
        return this == FAILED;
    }
}
