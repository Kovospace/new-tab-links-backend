package com.kovospace.newtablinks.payment.models;

/**
 * Which pro plan an account holds, as the website names it.
 *
 * <p>Not {@link ProPlan}, which names what can be bought at checkout: this names what is held,
 * and the website's contract spells the recurring plan {@code YEARLY_RECURRING}.</p>
 *
 * @since 0.0.9
 */
public enum SubscriptionPlan {

    /** Billed yearly, pro while it is paid. */
    YEARLY_RECURRING,

    /** One payment, pro for good. */
    LIFETIME
}
