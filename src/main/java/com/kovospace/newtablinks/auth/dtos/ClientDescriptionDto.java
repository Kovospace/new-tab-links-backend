package com.kovospace.newtablinks.auth.dtos;

import com.kovospace.newtablinks.auth.utils.BrowserNameParser;
import java.util.UUID;

/**
 * Where a sign-in is coming from, as far as the server can tell.
 *
 * <p>Assembled at the controller boundary from what the client claims plus what the transport
 * reveals, so that services below never touch HTTP headers.</p>
 *
 * @param deviceName     machine name supplied by the client, already defaulted
 * @param browserName    browser derived from the user agent
 * @param installationId identifier the client installation minted for itself, {@code null} when
 *                       it has none - the website, or an extension older than this field
 * @since 0.0.3
 */
public record ClientDescriptionDto(
        String deviceName,
        String browserName,
        UUID installationId) {

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
     * @param claimedDeviceName     value of the device name header, may be {@code null} or blank
     * @param userAgentHeader       value of the user agent header, may be {@code null}
     * @param claimedInstallationId value of the installation header, may be {@code null}, blank or
     *                              not a UUID at all
     * @return the description; the names are always populated, the installation may be
     *         {@code null}
     */
    public static ClientDescriptionDto from(
            final String claimedDeviceName,
            final String userAgentHeader,
            final String claimedInstallationId) {

        return new ClientDescriptionDto(
                normaliseDeviceName(claimedDeviceName),
                BrowserNameParser.parseBrowserName(userAgentHeader),
                parseInstallationId(claimedInstallationId));
    }

    /**
     * Reads the installation header, treating anything unusable as absent.
     *
     * <p>A malformed value is not worth failing a sign-in over, for the same reason an
     * over-long device name is not: this labels a row in a list. A client sending nonsense here
     * gets the behaviour of a client sending nothing, which is the old behaviour.</p>
     *
     * @param claimedInstallationId value supplied by the client
     * @return the identifier, or {@code null} when there is not a usable one
     */
    private static UUID parseInstallationId(final String claimedInstallationId) {
        if (claimedInstallationId == null || claimedInstallationId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(claimedInstallationId.trim());
        } catch (final IllegalArgumentException notAnIdentifier) {
            return null;
        }
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
