package com.kovospace.newtablinks.payment.utils;

import com.kovospace.newtablinks.common.exceptions.MalformedWebhookPayloadException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import tools.jackson.databind.JsonNode;

/**
 * Reads single fields out of a provider's JSON payload, tolerating what providers vary.
 *
 * <p>Two tolerances matter. A field may be absent, {@code null} or of an unexpected type, and
 * each of those reads as "not reported" rather than failing the whole event. And a reference to
 * another object may arrive either as that object or as its bare identifier - Creem's schema
 * declares {@code product}, {@code customer} and {@code subscription} as "string or object" - so
 * {@link #identifierOf(JsonNode)} accepts both.</p>
 *
 * @since 0.0.9
 */
public final class WebhookPayloadFields {

    /**
     * Prevents instantiation of this utility.
     */
    private WebhookPayloadFields() {
    }

    /**
     * Reads a string field.
     *
     * @param container the object holding the field
     * @param fieldName the field's name
     * @return the value, or {@code null} when absent, null, blank or not a string
     */
    public static String textOrNull(final JsonNode container, final String fieldName) {
        final JsonNode field = container.path(fieldName);
        if (!field.isString() || field.stringValue().isBlank()) {
            return null;
        }
        return field.stringValue();
    }

    /**
     * Reads a string field that every delivery must carry.
     *
     * @param container the object holding the field
     * @param fieldName the field's name
     * @return the value
     * @throws MalformedWebhookPayloadException when it is missing
     */
    public static String requiredText(final JsonNode container, final String fieldName) {
        final String value = textOrNull(container, fieldName);
        if (value == null) {
            throw new MalformedWebhookPayloadException("Missing field: " + fieldName);
        }
        return value;
    }

    /**
     * Reads a whole-number field.
     *
     * @param container the object holding the field
     * @param fieldName the field's name
     * @return the value, or {@code null} when absent or not a whole number
     */
    public static Long longOrNull(final JsonNode container, final String fieldName) {
        final JsonNode field = container.path(fieldName);
        return field.isIntegralNumber() ? field.longValue() : null;
    }

    /**
     * Reads an ISO-8601 timestamp field.
     *
     * @param container the object holding the field
     * @param fieldName the field's name
     * @return the instant, or {@code null} when absent
     * @throws MalformedWebhookPayloadException when present but not a timestamp
     */
    public static Instant instantOrNull(final JsonNode container, final String fieldName) {
        final String value = textOrNull(container, fieldName);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (final DateTimeParseException notATimestamp) {
            throw new MalformedWebhookPayloadException("Not an ISO-8601 timestamp: " + fieldName);
        }
    }

    /**
     * Reads the identifier of a referenced object, whichever way it was sent.
     *
     * @param reference the field holding the reference: an object with an {@code id}, a bare
     *                  identifier string, or missing
     * @return the identifier, or {@code null} when there is none
     */
    public static String identifierOf(final JsonNode reference) {
        if (reference.isString()) {
            return reference.stringValue().isBlank() ? null : reference.stringValue();
        }
        return reference.isObject() ? textOrNull(reference, "id") : null;
    }
}
