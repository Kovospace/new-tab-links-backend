package com.kovospace.newtablinks.sync.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kovospace.newtablinks.closedtab.mappers.ClosedTabMapperImpl;
import com.kovospace.newtablinks.closedtab.models.ClosedTabEntity;
import com.kovospace.newtablinks.closedtab.repositories.ClosedTabRepository;
import com.kovospace.newtablinks.group.mappers.GroupMapper;
import com.kovospace.newtablinks.group.repositories.GroupRepository;
import com.kovospace.newtablinks.link.mappers.LinkMapper;
import com.kovospace.newtablinks.link.repositories.LinkRepository;
import com.kovospace.newtablinks.profile.models.ProfileEntity;
import com.kovospace.newtablinks.profile.services.ProfileService;
import com.kovospace.newtablinks.subgroup.mappers.SubgroupMapper;
import com.kovospace.newtablinks.subgroup.repositories.SubgroupRepository;
import com.kovospace.newtablinks.sync.dtos.SyncSnapshotDto;
import com.kovospace.newtablinks.user.services.AccountPlanLimitsService;
import com.kovospace.newtablinks.user.services.UserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that a pulled snapshot carries the owner's closed tabs.
 *
 * <p>A pull replaces a profile wholesale, so a record the snapshot leaves out is a record every
 * other device deletes the next time it synchronizes. That makes the snapshot the other half of
 * the contract the push is the first half of, and worth pinning per kind of record.</p>
 *
 * <p>The closed tab half of the wiring is assembled for real - repository, mapper and reader -
 * because the mapping is where a field silently goes missing. Everything else is a mock: this is
 * a test about one collection, not about the other five.</p>
 *
 * @since 0.0.8
 */
class SyncSnapshotServiceTest {

    /** The account being snapshotted. */
    private static final UUID OWNER_ID = UUID.randomUUID();

    private final ClosedTabRepository closedTabRepository = mock(ClosedTabRepository.class);

    private final SyncSnapshotService syncSnapshotService = new SyncSnapshotService(
            mock(UserService.class),
            mock(GroupRepository.class),
            mock(GroupMapper.class),
            mock(SubgroupRepository.class),
            mock(SubgroupMapper.class),
            mock(LinkRepository.class),
            mock(LinkMapper.class),
            mock(EnvironmentSnapshotReader.class),
            new ClosedTabSnapshotReader(closedTabRepository, new ClosedTabMapperImpl()),
            mock(ProfileService.class),
            mock(AccountPlanLimitsService.class));

    @Test
    @DisplayName("returns the owner's closed tabs, newest first, with every field of each")
    void returnsTheClosedTabsOfTheOwner() {

        final UUID profileId = UUID.randomUUID();
        final ClosedTabEntity newer = closedTab(
                profileId, "https://spring.io", "Spring Boot reference",
                "https://spring.io/icon.png", Instant.parse("2026-09-11T09:00:00Z"), "Laptop");
        final ClosedTabEntity older = closedTab(
                profileId, "https://example.test", "", null,
                Instant.parse("2026-09-10T18:42:11Z"), null);

        when(closedTabRepository.findAllByOwnerIdOrderedByMostRecentlyClosed(OWNER_ID))
                .thenReturn(List.of(newer, older));

        final SyncSnapshotDto snapshot = syncSnapshotService.captureSnapshotForUser(OWNER_ID);

        assertThat(snapshot.closedTabs()).hasSize(2);
        assertThat(snapshot.closedTabs().getFirst()).satisfies(pulled -> {
            assertThat(pulled.id()).isEqualTo(newer.getId());
            assertThat(pulled.profileId()).isEqualTo(profileId);
            assertThat(pulled.url()).isEqualTo("https://spring.io");
            assertThat(pulled.title()).isEqualTo("Spring Boot reference");
            assertThat(pulled.faviconUrl()).isEqualTo("https://spring.io/icon.png");
            assertThat(pulled.closedAt()).isEqualTo(Instant.parse("2026-09-11T09:00:00Z"));
            assertThat(pulled.deviceName()).isEqualTo("Laptop");
        });

        // The order is the one the client renders in, and the server is what decides it: the
        // second entry is the older one, not merely "the other one".
        assertThat(snapshot.closedTabs().get(1).closedAt())
                .isEqualTo(Instant.parse("2026-09-10T18:42:11Z"));
    }

    @Test
    @DisplayName("reports an account with no closed tabs as an empty list rather than null")
    void returnsAnEmptyListWhenThereAreNoClosedTabs() {

        // A client that has just deleted its last entry pulls this, and null would be a crash on
        // a list it iterates without thinking about it.
        assertThat(syncSnapshotService.captureSnapshotForUser(OWNER_ID).closedTabs()).isEmpty();
    }

    /**
     * Builds a stored closed tab, identified and hung off a profile.
     *
     * @param profileId  identifier of the profile it is on
     * @param url        address the tab was showing
     * @param title      what the page called itself
     * @param faviconUrl favicon the browser had, may be {@code null}
     * @param closedAt   moment the tab was closed
     * @param deviceName device it was closed on, may be {@code null}
     * @return the entity
     */
    private static ClosedTabEntity closedTab(
            final UUID profileId,
            final String url,
            final String title,
            final String faviconUrl,
            final Instant closedAt,
            final String deviceName) {

        final ProfileEntity profile = mock(ProfileEntity.class);
        when(profile.getId()).thenReturn(profileId);

        final ClosedTabEntity closedTab =
                new ClosedTabEntity(profile, url, title, faviconUrl, closedAt, deviceName);
        closedTab.setId(UUID.randomUUID());
        return closedTab;
    }
}
