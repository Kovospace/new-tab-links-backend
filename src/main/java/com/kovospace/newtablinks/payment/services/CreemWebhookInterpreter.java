package com.kovospace.newtablinks.payment.services;

import static com.kovospace.newtablinks.payment.utils.WebhookPayloadFields.longOrNull;
import static com.kovospace.newtablinks.payment.utils.WebhookPayloadFields.requiredText;

import com.kovospace.newtablinks.common.exceptions.MalformedWebhookPayloadException;
import com.kovospace.newtablinks.common.exceptions.PaymentProviderNotConfiguredException;
import com.kovospace.newtablinks.common.exceptions.WebhookSignatureRejectedException;
import com.kovospace.newtablinks.entitlement.models.PaymentProvider;
import com.kovospace.newtablinks.payment.config.CreemProperties;
import com.kovospace.newtablinks.payment.models.InterpretedPaymentWebhook;
import com.kovospace.newtablinks.payment.utils.HmacSha256Signatures;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Creem's implementation of {@link PaymentWebhookInterpreter}.
 *
 * <p>Creem signs the raw request body with HMAC-SHA256, keyed with the endpoint's signing
 * secret, and sends the lower-case hex digest in the {@value #SIGNATURE_HEADER} header. The
 * envelope is {@code {"id", "eventType", "created_at", "object"}}, with {@code created_at} in
 * epoch milliseconds.</p>
 *
 * @since 0.0.9
 */
@Service
public class CreemWebhookInterpreter implements PaymentWebhookInterpreter {

    /** The header Creem sends the signature in. */
    public static final String SIGNATURE_HEADER = "creem-signature";

    private final CreemProperties creemProperties;
    private final CreemEntitlementSignalTranslator signalTranslator;
    private final ObjectMapper objectMapper;

    /**
     * Creates the interpreter.
     *
     * @param creemProperties  supplies the webhook signing secret
     * @param signalTranslator translates a verified event's object
     * @param objectMapper     parses the body once the signature has been verified
     */
    public CreemWebhookInterpreter(
            final CreemProperties creemProperties,
            final CreemEntitlementSignalTranslator signalTranslator,
            final ObjectMapper objectMapper) {

        this.creemProperties = creemProperties;
        this.signalTranslator = signalTranslator;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public InterpretedPaymentWebhook verifyAndInterpret(
            final byte[] requestBody,
            final String presentedSignature) {

        if (!creemProperties.isWebhookConfigured()) {
            throw new PaymentProviderNotConfiguredException(
                    "Payment webhooks are not enabled on this server");
        }
        final byte[] body = requestBody == null ? new byte[0] : requestBody;
        final byte[] secret = creemProperties.webhookSecret().getBytes(StandardCharsets.UTF_8);
        if (!HmacSha256Signatures.isValidHexSignature(secret, body, presentedSignature)) {
            throw new WebhookSignatureRejectedException();
        }
        return interpretVerifiedBody(body);
    }

    /**
     * Reads the envelope of a body whose signature has been verified.
     *
     * @param verifiedBody the body, known to come from Creem
     * @return the interpreted event
     */
    private InterpretedPaymentWebhook interpretVerifiedBody(final byte[] verifiedBody) {
        final JsonNode event = parse(verifiedBody);
        final String eventId = requiredText(event, "id");
        final String eventType = requiredText(event, "eventType");
        final Long createdAtEpochMillis = longOrNull(event, "created_at");
        if (createdAtEpochMillis == null) {
            throw new MalformedWebhookPayloadException("Missing field: created_at");
        }
        final JsonNode eventObject = event.path("object");
        if (!eventObject.isObject()) {
            throw new MalformedWebhookPayloadException("Missing field: object");
        }

        return new InterpretedPaymentWebhook(
                PaymentProvider.CREEM,
                eventId,
                eventType,
                signalTranslator.translate(
                        eventType, Instant.ofEpochMilli(createdAtEpochMillis), eventObject)
                        .orElse(null));
    }

    /**
     * Parses the body as JSON, straight from its bytes.
     *
     * @param verifiedBody the body
     * @return the root object
     * @throws MalformedWebhookPayloadException when it is not a JSON object
     */
    private JsonNode parse(final byte[] verifiedBody) {
        try {
            final JsonNode root = objectMapper.readTree(verifiedBody);
            if (root == null || !root.isObject()) {
                throw new MalformedWebhookPayloadException("Body is not a JSON object");
            }
            return root;
        } catch (final JacksonException notJson) {
            throw new MalformedWebhookPayloadException("Body is not valid JSON");
        }
    }
}
