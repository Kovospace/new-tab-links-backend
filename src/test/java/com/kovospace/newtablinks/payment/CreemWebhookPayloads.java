package com.kovospace.newtablinks.payment;

import com.kovospace.newtablinks.payment.models.CreemCheckoutMetadata;
import com.kovospace.newtablinks.payment.utils.HmacSha256Signatures;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Creem webhook bodies for tests, shaped like the samples in https://docs.creem.io/code/webhooks.
 *
 * <p>Trimmed to the fields this service reads plus a few it ignores, and with the customer named
 * with non-ASCII characters on purpose: the signature must hold over the raw UTF-8 bytes.</p>
 *
 * @since 0.0.9
 */
public final class CreemWebhookPayloads {

    /** A webhook signing secret, in the shape Creem issues them. */
    public static final String WEBHOOK_SECRET = "whsec_test_4f8b2c1d9e7a6b5c";

    /** A customer name that is different in every single-byte charset. */
    public static final String NON_ASCII_CUSTOMER_NAME = "Ján Kováč-Šťastný 日本";

    /**
     * Prevents instantiation.
     */
    private CreemWebhookPayloads() {
    }

    /**
     * Signs a body the way Creem does.
     *
     * @param body the raw body
     * @return the {@code creem-signature} header value
     */
    public static String sign(final byte[] body) {
        return HmacSha256Signatures.signToHex(
                WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), body);
    }

    /**
     * Encodes a body as Creem sends it: UTF-8.
     *
     * @param json the body
     * @return its bytes
     */
    public static byte[] utf8(final String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A completed one-time checkout: the lifetime purchase.
     *
     * @param eventId    the event identifier
     * @param occurredAt the event timestamp
     * @param accountId  the account in the checkout metadata, or {@code null} for none
     * @return the body
     */
    public static String lifetimeCheckoutCompleted(
            final String eventId, final Instant occurredAt, final UUID accountId) {

        return """
                {
                  "id": "%s",
                  "eventType": "checkout.completed",
                  "created_at": %d,
                  "object": {
                    "id": "ch_4l0N34kxo16AhRKUHFUuXr",
                    "object": "checkout",
                    "request_id": "6d1f0b52-3a1e-4f1c-9f0e-2b7c8a1d5e44",
                    "order": {
                      "id": "ord_lifetime_1",
                      "customer": "cust_1OcIK1GEuVvXZwD19tjq2z",
                      "product": "prod_lifetime",
                      "amount": 1499,
                      "amount_paid": 1814,
                      "currency": "EUR",
                      "status": "paid",
                      "type": "onetime",
                      "mode": "test"
                    },
                    "product": { "id": "prod_lifetime", "name": "Lifetime", "price": 1499,
                                 "currency": "EUR", "billing_type": "onetime" },
                    "customer": { "id": "cust_1OcIK1GEuVvXZwD19tjq2z", "object": "customer",
                                  "email": "customer@example.com", "name": "%s", "country": "SK" },
                    "custom_fields": [],
                    "status": "completed",
                    "metadata": %s,
                    "mode": "test"
                  }
                }
                """.formatted(eventId, occurredAt.toEpochMilli(), NON_ASCII_CUSTOMER_NAME,
                metadataOf(accountId));
    }

    /**
     * A completed recurring checkout: the first payment of a subscription.
     *
     * @param eventId        the event identifier
     * @param occurredAt     the event timestamp
     * @param accountId      the account in the checkout metadata, or {@code null} for none
     * @param subscriptionId the subscription identifier
     * @param periodEnd      end of the first paid period
     * @return the body
     */
    public static String subscriptionCheckoutCompleted(
            final String eventId, final Instant occurredAt, final UUID accountId,
            final String subscriptionId, final Instant periodEnd) {

        return """
                {
                  "id": "%s",
                  "eventType": "checkout.completed",
                  "created_at": %d,
                  "object": {
                    "id": "ch_subscription_1",
                    "object": "checkout",
                    "order": { "id": "ord_subscription_1", "customer": "cust_sub",
                               "product": "prod_yearly", "amount": 468, "amount_paid": 566,
                               "currency": "EUR", "status": "paid", "type": "recurring" },
                    "product": { "id": "prod_yearly", "billing_type": "recurring",
                                 "billing_period": "every-year" },
                    "customer": { "id": "cust_sub", "name": "%s" },
                    "subscription": { "id": "%s", "object": "subscription",
                                      "product": "prod_yearly", "customer": "cust_sub",
                                      "status": "active",
                                      "current_period_end_date": "%s",
                                      "metadata": %s },
                    "status": "completed",
                    "metadata": %s
                  }
                }
                """.formatted(eventId, occurredAt.toEpochMilli(), NON_ASCII_CUSTOMER_NAME,
                subscriptionId, periodEnd, metadataOf(accountId), metadataOf(accountId));
    }

    /**
     * A subscription lifecycle event, for any {@code subscription.*} type.
     *
     * @param eventType      e.g. {@code subscription.paid}
     * @param eventId        the event identifier
     * @param occurredAt     the event timestamp
     * @param accountId      the account in the subscription metadata, or {@code null} for none
     * @param subscriptionId the subscription identifier
     * @param periodEnd      end of the current period
     * @return the body
     */
    public static String subscriptionEvent(
            final String eventType, final String eventId, final Instant occurredAt,
            final UUID accountId, final String subscriptionId, final Instant periodEnd) {

        return """
                {
                  "id": "%s",
                  "eventType": "%s",
                  "created_at": %d,
                  "object": {
                    "id": "%s",
                    "object": "subscription",
                    "product": { "id": "prod_yearly", "price": 468, "currency": "EUR",
                                 "billing_type": "recurring", "billing_period": "every-year" },
                    "customer": { "id": "cust_sub", "email": "customer@example.com",
                                  "name": "%s", "country": "SK" },
                    "collection_method": "charge_automatically",
                    "status": "active",
                    "last_transaction_id": "tran_1",
                    "last_transaction": { "id": "tran_1", "amount": 468, "amount_paid": 566,
                                          "currency": "EUR", "status": "paid" },
                    "current_period_start_date": "2026-01-01T00:00:00.000Z",
                    "current_period_end_date": "%s",
                    "canceled_at": null,
                    "metadata": %s,
                    "mode": "test"
                  }
                }
                """.formatted(eventId, eventType, occurredAt.toEpochMilli(), subscriptionId,
                NON_ASCII_CUSTOMER_NAME, periodEnd, metadataOf(accountId));
    }

    /**
     * A refund of an order.
     *
     * @param eventId        the event identifier
     * @param occurredAt     the event timestamp
     * @param orderId        the refunded order
     * @param amountPaid     what the transaction had charged
     * @param amountRefunded what was given back
     * @return the body
     */
    public static String refundCreated(
            final String eventId, final Instant occurredAt, final String orderId,
            final long amountPaid, final long amountRefunded) {

        return """
                {
                  "id": "%s",
                  "eventType": "refund.created",
                  "created_at": %d,
                  "object": {
                    "id": "ref_1",
                    "object": "refund",
                    "status": "succeeded",
                    "refund_amount": %d,
                    "refund_currency": "EUR",
                    "reason": "requested_by_customer",
                    "transaction": { "id": "tran_2", "amount_paid": %d, "currency": "EUR",
                                     "status": "refunded", "refunded_amount": %d,
                                     "order": "%s" },
                    "order": { "id": "%s", "type": "onetime" },
                    "customer": { "id": "cust_1OcIK1GEuVvXZwD19tjq2z" }
                  }
                }
                """.formatted(eventId, occurredAt.toEpochMilli(), amountRefunded, amountPaid,
                amountRefunded, orderId, orderId);
    }

    /**
     * Renders the metadata object carrying an account, or an empty one.
     *
     * @param accountId the account, or {@code null}
     * @return the JSON object
     */
    private static String metadataOf(final UUID accountId) {
        return accountId == null
                ? "{}"
                : "{ \"%s\": \"%s\", \"campaign\": \"launch\" }"
                        .formatted(CreemCheckoutMetadata.ACCOUNT_ID_KEY, accountId);
    }
}
