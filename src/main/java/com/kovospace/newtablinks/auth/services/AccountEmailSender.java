package com.kovospace.newtablinks.auth.services;

/**
 * Sends the messages the account flows depend on.
 *
 * <p>An interface rather than a concrete sender so that the transactional provider, and the
 * decision to send at all, stay out of the services that hold the business logic.</p>
 *
 * @since 0.0.2
 */
public interface AccountEmailSender {

    /**
     * Sends the activation link to a newly registered address.
     *
     * @param recipientAddress address to send to
     * @param displayName      name to greet the recipient by
     * @param activationLink   absolute link that activates the account
     */
    void sendActivationLink(String recipientAddress, String displayName, String activationLink);

    /**
     * Tells the owner of an already registered address that someone tried to register it again.
     *
     * <p>This is the other half of not revealing whether an address is taken: the attempt gets
     * the same bland answer either way, and the truth goes only to the address itself - where it
     * doubles as a warning that somebody is poking at the account.</p>
     *
     * @param recipientAddress address that is already registered
     */
    void sendAddressAlreadyRegisteredNotice(String recipientAddress);

    /**
     * Sends a password reset link.
     *
     * @param recipientAddress address to send to
     * @param displayName      name to greet the recipient by
     * @param passwordResetLink absolute link that lets a new password be set
     */
    void sendPasswordResetLink(String recipientAddress, String displayName, String passwordResetLink);
}
