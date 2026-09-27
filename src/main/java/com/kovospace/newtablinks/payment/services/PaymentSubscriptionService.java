package com.kovospace.newtablinks.payment.services;

import com.kovospace.newtablinks.entitlement.services.EntitlementStandingService;
import com.kovospace.newtablinks.payment.dtos.SubscriptionStatusDto;
import com.kovospace.newtablinks.payment.mappers.SubscriptionStatusMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells an account which pro plan it holds and where that plan stands.
 *
 * <p>Answers from the stored entitlement only; it never asks the payment provider, so it is as
 * current as the last webhook.</p>
 *
 * @since 0.0.9
 */
@Service
public class PaymentSubscriptionService {

    private final EntitlementStandingService entitlementStandingService;
    private final SubscriptionStatusMapper subscriptionStatusMapper;

    /**
     * Creates the service.
     *
     * @param entitlementStandingService reads the account's entitlement
     * @param subscriptionStatusMapper   converts it to the website's shape
     */
    public PaymentSubscriptionService(
            final EntitlementStandingService entitlementStandingService,
            final SubscriptionStatusMapper subscriptionStatusMapper) {

        this.entitlementStandingService = entitlementStandingService;
        this.subscriptionStatusMapper = subscriptionStatusMapper;
    }

    /**
     * Describes the account's plan.
     *
     * <p>Never fails for an account without one: that is a plan of {@code null} in state
     * {@code NONE}.</p>
     *
     * @param ownerId identifier of the account
     * @return its plan, state and dates
     */
    @Transactional(readOnly = true)
    public SubscriptionStatusDto describeSubscriptionOf(final UUID ownerId) {
        return entitlementStandingService.findEntitlementEntity(ownerId)
                .map(subscriptionStatusMapper::toDto)
                .orElseGet(subscriptionStatusMapper::toDtoForAccountWithoutEntitlement);
    }
}
