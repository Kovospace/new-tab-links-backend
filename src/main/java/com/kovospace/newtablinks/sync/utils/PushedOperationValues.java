package com.kovospace.newtablinks.sync.utils;

import com.kovospace.newtablinks.sync.dtos.SyncRejectionReason;
import com.kovospace.newtablinks.sync.exceptions.SyncOperationRejectedException;
import java.util.UUID;

/**
 * Reads the fields a pushed operation must carry, refusing the operation when one is missing.
 *
 * <p>{@link com.kovospace.newtablinks.sync.dtos.SyncOperationDto} is one flat shape covering five
 * kinds of record, so bean validation can only say that a field fits - not that this kind of
 * operation needed it. That check happens here instead, where it can refuse a single operation
 * rather than the whole batch.</p>
 *
 * @since 0.0.6
 */
public final class PushedOperationValues {

    /**
     * Not instantiable; this class only holds static helpers.
     */
    private PushedOperationValues() {
        throw new AssertionError(
                "PushedOperationValues is a utility class and must not be instantiated");
    }

    /**
     * Returns a text the operation had to carry.
     *
     * @param suppliedText the value read from the operation
     * @return the same value
     * @throws SyncOperationRejectedException when it is {@code null} or blank
     */
    public static String requireSuppliedText(final String suppliedText) {
        if (suppliedText == null || suppliedText.isBlank()) {
            throw new SyncOperationRejectedException(SyncRejectionReason.MISSING_REQUIRED_VALUE);
        }
        return suppliedText;
    }

    /**
     * Returns an identifier the operation had to carry.
     *
     * @param suppliedIdentifier the value read from the operation
     * @return the same value
     * @throws SyncOperationRejectedException when it is {@code null}
     */
    public static UUID requireSuppliedIdentifier(final UUID suppliedIdentifier) {
        if (suppliedIdentifier == null) {
            throw new SyncOperationRejectedException(SyncRejectionReason.MISSING_REQUIRED_VALUE);
        }
        return suppliedIdentifier;
    }

    /**
     * Returns the display position the operation had to carry.
     *
     * <p>Not defaulted when absent. A position decides what the user sees, and inventing one
     * would quietly reorder somebody's page rather than telling the client it sent a broken
     * operation.</p>
     *
     * @param suppliedPosition the value read from the operation
     * @return the same value, unboxed
     * @throws SyncOperationRejectedException when it is {@code null}
     */
    public static int requireSuppliedPosition(final Integer suppliedPosition) {
        if (suppliedPosition == null) {
            throw new SyncOperationRejectedException(SyncRejectionReason.MISSING_REQUIRED_VALUE);
        }
        return suppliedPosition;
    }

    /**
     * Reads an optional flag, treating an absent one as not set.
     *
     * @param suppliedFlag the value read from the operation, may be {@code null}
     * @return {@code false} when it is {@code null}, otherwise its value
     */
    public static boolean flagOrFalse(final Boolean suppliedFlag) {
        return suppliedFlag != null && suppliedFlag;
    }
}
