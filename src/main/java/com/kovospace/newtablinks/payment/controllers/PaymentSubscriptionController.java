package com.kovospace.newtablinks.payment.controllers;

import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.payment.dtos.SubscriptionStatusDto;
import com.kovospace.newtablinks.payment.services.PaymentSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shows the signed-in account which pro plan it holds.
 *
 * <p>Needs a bearer token like every other endpoint: the security configuration exempts only
 * {@code POST} to the webhook path, and this path is not under it.</p>
 *
 * @since 0.0.9
 */
@RestController
@Tag(name = "Payments", description = "Buying pro, and the provider's notifications about it")
public class PaymentSubscriptionController {

    private final PaymentSubscriptionService paymentSubscriptionService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    /**
     * Creates the controller.
     *
     * @param paymentSubscriptionService describes the account's plan
     * @param authenticatedUserProvider  identifies the account from its access token
     */
    public PaymentSubscriptionController(
            final PaymentSubscriptionService paymentSubscriptionService,
            final AuthenticatedUserProvider authenticatedUserProvider) {

        this.paymentSubscriptionService = paymentSubscriptionService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    /**
     * Returns the signed-in account's plan and where it stands.
     *
     * @return the plan; state {@code NONE} when the account has never held one
     */
    @GetMapping(ApiEndpointPaths.PAYMENT_SUBSCRIPTION_PATH)
    @Operation(summary = "Show the signed-in account's pro plan",
            description = "Always answers for a signed-in account; one that never bought anything "
                    + "gets plan null and state NONE. Read from the stored entitlement, so it is "
                    + "as current as the payment provider's last webhook. Whether the account is "
                    + "pro right now is premium on GET /api/v1/users/me, not derived from this. "
                    + "cancellable and refundable are always false for now: no cancel or refund "
                    + "endpoint exists yet.")
    @ApiResponse(responseCode = "200", description = "The account's plan")
    @ApiResponse(responseCode = "401", description = "No valid access token was presented",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public SubscriptionStatusDto getMySubscription() {
        return paymentSubscriptionService.describeSubscriptionOf(
                authenticatedUserProvider.getAuthenticatedUserId());
    }
}
