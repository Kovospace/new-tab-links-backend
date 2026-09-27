package com.kovospace.newtablinks.payment.services;

import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.NON_ASCII_CUSTOMER_NAME;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.WEBHOOK_SECRET;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.lifetimeCheckoutCompleted;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.refundCreated;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.sign;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.subscriptionCheckoutCompleted;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.subscriptionEvent;
import static com.kovospace.newtablinks.payment.CreemWebhookPayloads.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kovospace.newtablinks.common.exceptions.MalformedWebhookPayloadException;
import com.kovospace.newtablinks.common.exceptions.PaymentProviderNotConfiguredException;
import com.kovospace.newtablinks.common.exceptions.WebhookSignatureRejectedException;
import com.kovospace.newtablinks.entitlement.models.ChargedAmount;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignal;
import com.kovospace.newtablinks.entitlement.models.EntitlementSignalKind;
import com.kovospace.newtablinks.payment.config.CreemProperties;
import com.kovospace.newtablinks.payment.models.InterpretedPaymentWebhook;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests that a Creem delivery is verified over its exact bytes and translated faithfully.
 *
 * @since 0.0.9
 */
class CreemWebhookInterpreterTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("0c9a3a8e-5a8b-4a7e-9b3c-3f1f2c4d5e6f");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-27T10:15:30.123Z");
    private static final Instant PERIOD_END = Instant.parse("2027-09-27T10:15:30Z");

    private final CreemWebhookInterpreter interpreter = interpreterWithSecret(WEBHOOK_SECRET);

    @Test
    @DisplayName("accepts a correctly signed body whose customer name is not ASCII")
    void shouldAcceptACorrectlySignedNonAsciiBody() {
        final byte[] body = utf8(lifetimeCheckoutCompleted("evt_1", OCCURRED_AT, ACCOUNT_ID));
        assertThat(new String(body, StandardCharsets.UTF_8)).contains(NON_ASCII_CUSTOMER_NAME);

        final InterpretedPaymentWebhook webhook = interpreter.verifyAndInterpret(body, sign(body));

        assertThat(webhook.providerEventId()).isEqualTo("evt_1");
        assertThat(webhook.eventType()).isEqualTo("checkout.completed");
    }

    @Test
    @DisplayName("refuses the body once a single byte of the non-ASCII name changed")
    void shouldRefuseATamperedNonAsciiBody() {
        final byte[] body = utf8(lifetimeCheckoutCompleted("evt_1", OCCURRED_AT, ACCOUNT_ID));
        final String signature = sign(body);
        final byte[] tampered = Arrays.copyOf(body, body.length);
        final int firstNonAsciiByte = indexOfFirstNonAsciiByte(tampered);
        tampered[firstNonAsciiByte] ^= 0x01;

        assertThatThrownBy(() -> interpreter.verifyAndInterpret(tampered, signature))
                .isInstanceOf(WebhookSignatureRejectedException.class);
    }

    @Test
    @DisplayName("a body round-tripped through ISO-8859-1, as a String binding would, no longer "
            + "verifies - which is why the controller binds byte[]")
    void shouldNotVerifyABodyDecodedWithTheWrongCharset() {
        final byte[] body = utf8(lifetimeCheckoutCompleted("evt_1", OCCURRED_AT, ACCOUNT_ID));
        final String signature = sign(body);
        final byte[] reencoded = new String(body, StandardCharsets.ISO_8859_1)
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> interpreter.verifyAndInterpret(reencoded, signature))
                .isInstanceOf(WebhookSignatureRejectedException.class);
    }

    @Test
    @DisplayName("refuses a missing signature, a missing body and a body signed with another secret")
    void shouldRefuseUnsignedOrForeignSignedBodies() {
        final byte[] body = utf8(lifetimeCheckoutCompleted("evt_1", OCCURRED_AT, ACCOUNT_ID));
        final CreemWebhookInterpreter otherSecret = interpreterWithSecret("whsec_someone_else");

        assertThatThrownBy(() -> interpreter.verifyAndInterpret(body, null))
                .isInstanceOf(WebhookSignatureRejectedException.class);
        assertThatThrownBy(() -> interpreter.verifyAndInterpret(null, sign(body)))
                .isInstanceOf(WebhookSignatureRejectedException.class);
        assertThatThrownBy(() -> otherSecret.verifyAndInterpret(body, sign(body)))
                .isInstanceOf(WebhookSignatureRejectedException.class);
    }

    @Test
    @DisplayName("refuses every delivery when no webhook secret is configured")
    void shouldRefuseEverythingWithoutASecret() {
        final byte[] body = utf8(lifetimeCheckoutCompleted("evt_1", OCCURRED_AT, ACCOUNT_ID));

        assertThatThrownBy(() -> interpreterWithSecret("").verifyAndInterpret(body, sign(body)))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
    }

    @Test
    @DisplayName("reports a correctly signed body that is not a Creem event as malformed")
    void shouldReportASignedButUnreadableBody() {
        final byte[] notJson = utf8("not json at all");
        final byte[] noEventId = utf8("{\"eventType\":\"checkout.completed\",\"created_at\":1,"
                + "\"object\":{}}");

        assertThatThrownBy(() -> interpreter.verifyAndInterpret(notJson, sign(notJson)))
                .isInstanceOf(MalformedWebhookPayloadException.class);
        assertThatThrownBy(() -> interpreter.verifyAndInterpret(noEventId, sign(noEventId)))
                .isInstanceOf(MalformedWebhookPayloadException.class);
    }

    @Test
    @DisplayName("a one-time checkout is a lifetime purchase attributed through its metadata")
    void shouldTranslateALifetimePurchase() {
        final EntitlementSignal signal = signalOf(
                lifetimeCheckoutCompleted("evt_1", OCCURRED_AT, ACCOUNT_ID));

        assertThat(signal.kind()).isEqualTo(EntitlementSignalKind.LIFETIME_PURCHASED);
        assertThat(signal.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(signal.attributedAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(signal.references().orderId()).isEqualTo("ord_lifetime_1");
        assertThat(signal.references().customerId()).isEqualTo("cust_1OcIK1GEuVvXZwD19tjq2z");
        assertThat(signal.references().productId()).isEqualTo("prod_lifetime");
        assertThat(signal.references().subscriptionId()).isNull();
        assertThat(signal.paidUntil()).isNull();
        assertThat(signal.chargedAmount()).isEqualTo(new ChargedAmount(1814, "EUR"));
    }

    @Test
    @DisplayName("a recurring checkout starts a subscription, paid until its period end")
    void shouldTranslateASubscriptionPurchase() {
        final EntitlementSignal signal = signalOf(subscriptionCheckoutCompleted(
                "evt_2", OCCURRED_AT, ACCOUNT_ID, "sub_1", PERIOD_END));

        assertThat(signal.kind()).isEqualTo(EntitlementSignalKind.SUBSCRIPTION_PURCHASED);
        assertThat(signal.attributedAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(signal.references().subscriptionId()).isEqualTo("sub_1");
        assertThat(signal.paidUntil()).isEqualTo(PERIOD_END);
        assertThat(signal.chargedAmount()).isEqualTo(new ChargedAmount(566, "EUR"));
    }

    @Test
    @DisplayName("a renewal a year later is still attributed, through the metadata Creem copied "
            + "onto the subscription")
    void shouldAttributeARenewalThroughSubscriptionMetadata() {
        final EntitlementSignal signal = signalOf(subscriptionEvent(
                "subscription.paid", "evt_3", OCCURRED_AT, ACCOUNT_ID, "sub_1", PERIOD_END));

        assertThat(signal.kind()).isEqualTo(EntitlementSignalKind.SUBSCRIPTION_PAID);
        assertThat(signal.attributedAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(signal.paidUntil()).isEqualTo(PERIOD_END);
        assertThat(signal.chargedAmount()).isEqualTo(new ChargedAmount(566, "EUR"));
    }

    @Test
    @DisplayName("maps every documented subscription event onto its signal")
    void shouldMapEverySubscriptionEvent() {
        assertThat(kindOf("subscription.active")).isEqualTo(EntitlementSignalKind.SUBSCRIPTION_ACTIVATED);
        assertThat(kindOf("subscription.past_due"))
                .isEqualTo(EntitlementSignalKind.SUBSCRIPTION_PAYMENT_FAILED);
        assertThat(kindOf("subscription.unpaid"))
                .isEqualTo(EntitlementSignalKind.SUBSCRIPTION_PAYMENT_FAILED);
        assertThat(kindOf("subscription.scheduled_cancel"))
                .isEqualTo(EntitlementSignalKind.SUBSCRIPTION_CANCELLATION_SCHEDULED);
        assertThat(kindOf("subscription.canceled"))
                .isEqualTo(EntitlementSignalKind.SUBSCRIPTION_CANCELED);
        assertThat(kindOf("subscription.expired"))
                .isEqualTo(EntitlementSignalKind.SUBSCRIPTION_EXPIRED);
    }

    @Test
    @DisplayName("a failed renewal carries no charged amount")
    void shouldNotReportAChargeOnAFailedRenewal() {
        final EntitlementSignal signal = signalOf(subscriptionEvent(
                "subscription.past_due", "evt_4", OCCURRED_AT, ACCOUNT_ID, "sub_1", PERIOD_END));

        assertThat(signal.chargedAmount()).isNull();
    }

    @Test
    @DisplayName("acknowledges event types that do not affect an entitlement without a signal")
    void shouldLeaveOtherEventTypesAlone() {
        final byte[] body = utf8(subscriptionEvent(
                "subscription.trialing", "evt_5", OCCURRED_AT, ACCOUNT_ID, "sub_1", PERIOD_END));

        final InterpretedPaymentWebhook webhook = interpreter.verifyAndInterpret(body, sign(body));

        assertThat(webhook.signal()).isEmpty();
        assertThat(webhook.eventType()).isEqualTo("subscription.trialing");
    }

    @Test
    @DisplayName("a full refund is a refund signal for the order; a partial one is none")
    void shouldTranslateOnlyAFullRefund() {
        final EntitlementSignal fullRefund =
                signalOf(refundCreated("evt_6", OCCURRED_AT, "ord_lifetime_1", 1814, 1814));
        final byte[] partial =
                utf8(refundCreated("evt_7", OCCURRED_AT, "ord_lifetime_1", 1814, 500));

        assertThat(fullRefund.kind()).isEqualTo(EntitlementSignalKind.PAYMENT_REFUNDED);
        assertThat(fullRefund.references().orderId()).isEqualTo("ord_lifetime_1");
        assertThat(interpreter.verifyAndInterpret(partial, sign(partial)).signal()).isEmpty();
    }

    /**
     * Verifies and translates a body, expecting a signal.
     *
     * @param json the body
     * @return the signal
     */
    private EntitlementSignal signalOf(final String json) {
        final byte[] body = utf8(json);
        return interpreter.verifyAndInterpret(body, sign(body)).signal().orElseThrow();
    }

    /**
     * Translates a subscription event of the given type.
     *
     * @param eventType the Creem event type
     * @return the signal kind it became
     */
    private EntitlementSignalKind kindOf(final String eventType) {
        return signalOf(subscriptionEvent(
                eventType, "evt_" + eventType, OCCURRED_AT, ACCOUNT_ID, "sub_1", PERIOD_END))
                .kind();
    }

    /**
     * Finds the first byte of a multi-byte UTF-8 sequence.
     *
     * @param bytes the body
     * @return its index
     */
    private static int indexOfFirstNonAsciiByte(final byte[] bytes) {
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] < 0) {
                return index;
            }
        }
        throw new AssertionError("The fixture must contain a non-ASCII character");
    }

    /**
     * Builds an interpreter with the given webhook secret.
     *
     * @param webhookSecret the secret, blank for none
     * @return the interpreter
     */
    private static CreemWebhookInterpreter interpreterWithSecret(final String webhookSecret) {
        final CreemProperties properties = new CreemProperties(
                "", webhookSecret, "", Duration.ofSeconds(5), null, null);
        return new CreemWebhookInterpreter(
                properties, new CreemEntitlementSignalTranslator(), JsonMapper.builder().build());
    }
}
