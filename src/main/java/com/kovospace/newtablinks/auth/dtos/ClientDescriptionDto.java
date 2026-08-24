package com.kovospace.newtablinks.auth.dtos;

import com.kovospace.newtablinks.auth.utils.BrowserNameParser;

/**
 * Where a sign-in is coming from, as far as the server can tell.
 *
 * <p>Assembled at the controller boundary from what the client claims plus what the transport
 * reveals, so that services below never touch HTTP headers.</p>
 *
 * @param deviceName  machine name supplied by the client, already defaulted
 * @param browserName browser derived from the user agent
 * @since 0.0.3
 */
public record ClientDescriptionDto(String deviceName, String browserName) {

    /**
     * Label used when a client does not say what machine it is on.
     */
    public static final String UNNAMED_DEVICE = "Unnamed device";

    /**
     * Longest device name accepted; longer values are truncated rather than rejected, because a
     * silly name is not worth failing a sign-in over.
     */
    private static final int MAXIMUM_DEVICE_NAME_LENGTH = 120;

    /**
     * Builds a description from the raw request inputs.
     *
     * @param claimedDeviceName value of the device name header, may be {@code null} or blank
     * @param userAgentHeader   value of the user agent header, may be {@code null}
     * @return the description, with both fields always populated
     */
    public static ClientDescriptionDto from(
            final String claimedDeviceName,
            final String userAgentHeader) {

        return new ClientDescriptionDto(
                normaliseDeviceName(claimedDeviceName),
                BrowserNameParser.parseBrowserName(userAgentHeader));
    }

    /**
     * Trims and truncates a claimed device name, falling back to a placeholder.
     *
     * @param claimedDeviceName value supplied by the client
     * @return a usable device name, never blank
     */
    private static String normaliseDeviceName(final String claimedDeviceName) {
        if (claimedDeviceName == null || claimedDeviceName.isBlank()) {
            return UNNAMED_DEVICE;
        }
        final String trimmed = claimedDeviceName.trim();
        return trimmed.length() > MAXIMUM_DEVICE_NAME_LENGTH
                ? trimmed.substring(0, MAXIMUM_DEVICE_NAME_LENGTH)
                : trimmed;
    }
}
