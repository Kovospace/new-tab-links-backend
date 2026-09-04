package com.kovospace.newtablinks.sync.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovospace.newtablinks.sync.dtos.SyncEntityKind;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the bookkeeping that lets one push refer to records it has only just created.
 *
 * @since 0.0.5
 */
class SyncOperationContextTest {

    private final SyncOperationContext context = new SyncOperationContext(UUID.randomUUID());

    @Test
    @DisplayName("reports nothing when the server kept the identifier the client chose")
    void recordsNoRemappingWhenTheIdentifierSurvived() {

        final UUID identifier = UUID.randomUUID();

        context.recordRemapping(SyncEntityKind.LINK, identifier, identifier);

        assertThat(context.getRemappings()).isEmpty();
        assertThat(context.resolveStoredIdentifier(identifier)).isEqualTo(identifier);
    }

    @Test
    @DisplayName("reports a remapping when the server had to store a different identifier")
    void recordsARemappingWhenTheIdentifierChanged() {

        final UUID askedFor = UUID.randomUUID();
        final UUID stored = UUID.randomUUID();

        context.recordRemapping(SyncEntityKind.GROUP, askedFor, stored);

        assertThat(context.getRemappings()).singleElement().satisfies(remap -> {
            assertThat(remap.entityKind()).isEqualTo(SyncEntityKind.GROUP);
            assertThat(remap.clientId()).isEqualTo(askedFor);
            assertThat(remap.serverId()).isEqualTo(stored);
        });
    }

    @Test
    @DisplayName("translates a parent identifier that was remapped earlier in the same push")
    void resolvesAParentRemappedEarlierInTheBatch() {

        final UUID groupAsClientKnowsIt = UUID.randomUUID();
        final UUID groupAsStored = UUID.randomUUID();

        context.recordRemapping(SyncEntityKind.GROUP, groupAsClientKnowsIt, groupAsStored);

        // This is what makes a batch work at all: the link that follows the group names its
        // parent by the identifier the client invented, which no longer exists on the server.
        assertThat(context.resolveStoredIdentifier(groupAsClientKnowsIt)).isEqualTo(groupAsStored);
    }

    @Test
    @DisplayName("leaves an identifier it has never remapped alone")
    void resolvesAnUnknownIdentifierToItself() {

        final UUID untouched = UUID.randomUUID();

        assertThat(context.resolveStoredIdentifier(untouched)).isEqualTo(untouched);
    }

    @Test
    @DisplayName("keeps the first remapping when the same identifier is recorded twice")
    void keepsTheFirstRemappingOfAnIdentifier() {

        final UUID askedFor = UUID.randomUUID();
        final UUID storedFirst = UUID.randomUUID();
        final UUID storedLater = UUID.randomUUID();

        context.recordRemapping(SyncEntityKind.LINK, askedFor, storedFirst);
        context.recordRemapping(SyncEntityKind.LINK, askedFor, storedLater);

        assertThat(context.getRemappings()).hasSize(1);
        assertThat(context.resolveStoredIdentifier(askedFor)).isEqualTo(storedFirst);
    }

    @Test
    @DisplayName("ignores a remapping with nothing to remap")
    void ignoresNullIdentifiers() {

        context.recordRemapping(SyncEntityKind.LINK, null, UUID.randomUUID());
        context.recordRemapping(SyncEntityKind.LINK, UUID.randomUUID(), null);

        assertThat(context.getRemappings()).isEmpty();
    }
}
