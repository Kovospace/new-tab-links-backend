package com.kovospace.newtablinks.sync.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One change a client made while it was the only one that knew about it.
 *
 * <p>Flat, and wider than any single kind needs, on purpose. The alternative - a polymorphic
 * body discriminated by {@code type} - would buy stricter validation at the cost of a schema the
 * browser extension has to model five times and a Jackson configuration that fails obscurely when
 * the two repositories drift. A flat shape is what the extension already produces from its own
 * flat local state, and which fields matter for which kind is checked where it can be reported
 * per operation rather than as a blanket 400.</p>
 *
 * <p>A closed tab reuses {@code url}, {@code title}, {@code faviconUrl} and {@code profileId}
 * rather than growing four more fields beside them: the values mean the same thing and the flat
 * shape exists precisely so that it can be shared. Only {@code closedAt} and {@code deviceName}
 * are new, because nothing else here carries either.</p>
 *
 * <p>Every identifier here is chosen by the client. That is safe because it names nothing but a
 * row inside the caller's own account; see
 * {@link com.kovospace.newtablinks.common.utils.ClientAssignedIdentifierPolicy}.</p>
 *
 * @param operation        what to do with the record
 * @param entityKind       what kind of record it is
 * @param id               identifier the client uses for the record
 * @param profileId        parent profile, for an environment or a closed tab
 * @param environmentId    parent environment, for a group
 * @param parentGroupId    parent group, for a subgroup or a link
 * @param parentSubgroupId parent subgroup, for a link that is nested in one
 * @param name             display name, for a profile, environment, group or subgroup
 * @param description      free text, for an environment, group or subgroup
 * @param title            display text, for a link or a closed tab
 * @param url              address, for a link or a closed tab
 * @param faviconUrl       cached favicon address, for a link or a closed tab
 * @param collapsed        whether a subgroup is folded away right now
 * @param defaultCollapsed whether a subgroup starts folded away
 * @param catchLinksIntoTabGroup whether tabs navigating to a subgroup's links are pulled into its
 *                               browser tab group
 * @param enableDragAndDrop      whether a profile lets its links and groups be rearranged by
 *                               dragging
 * @param hideTips               whether a profile hides the tips shown on the new tab page
 *                               background
 * @param dismissedTips          identifiers of the tips a profile has dismissed one by one
 * @param color                  name of the Chrome tab group colour a subgroup is painted with,
 *                               absent when it has none
 * @param closedAt               moment a tab was closed, as the closing device reported it
 * @param deviceName             name of the device a tab was closed on, absent when it has none
 * @param position         zero based position among the record's siblings
 * @since 0.0.6
 */
@Schema(description = "One change a client made while offline")
public record SyncOperationDto(

        @Schema(description = "What to do with the record", example = "upsert")
        @JsonProperty("op") @NotNull SyncOperationKind operation,

        @Schema(description = "What kind of record it is", example = "link")
        @JsonProperty("type") @NotNull SyncEntityKind entityKind,

        @Schema(description = "Identifier the client uses for the record")
        @NotNull UUID id,

        @Schema(description = "Parent profile, for an environment or a closed tab")
        UUID profileId,

        @Schema(description = "Parent environment, for a group")
        UUID environmentId,

        @Schema(description = "Parent group, for a subgroup or a link")
        UUID parentGroupId,

        @Schema(description = "Parent subgroup, for a link nested in one")
        UUID parentSubgroupId,

        @Schema(description = "Display name, for a profile, environment, group or subgroup",
                example = "Documentation")
        @Size(max = 120) String name,

        @Schema(description = "Free text, for an environment, group or subgroup")
        @Size(max = 500) String description,

        @Schema(description = "Display text, for a link, or the page title of a closed tab",
                example = "Spring Boot reference")
        @Size(max = 200) String title,

        @Schema(description = "Address, for a link or a closed tab", example = "https://spring.io")
        @Size(max = 2048) String url,

        @Schema(description = "Cached favicon address, for a link or a closed tab")
        @Size(max = 2048) String faviconUrl,

        @Schema(description = "Whether a subgroup is folded away right now", example = "false")
        Boolean collapsed,

        @Schema(description = "Whether a subgroup starts folded away", example = "false")
        Boolean defaultCollapsed,

        @Schema(description = "Whether tabs navigating to a subgroup's links are pulled into its "
                + "browser tab group", example = "false")
        Boolean catchLinksIntoTabGroup,

        @Schema(description = "Whether a profile lets its links and groups be rearranged by "
                + "dragging", example = "false")
        Boolean enableDragAndDrop,

        @Schema(description = "Whether a profile hides the tips shown on the new tab page "
                + "background", example = "false")
        Boolean hideTips,

        @Schema(description = "Identifiers of the new tab page tips a profile has dismissed one "
                + "by one; absent means none. An upsert replaces the whole list",
                example = "[\"hide-tips\"]")
        @Size(max = 100) List<@NotBlank @Size(max = 64) String> dismissedTips,

        @Schema(description = "Name of the Chrome tab group colour a subgroup is painted with, "
                + "one of the names Chrome accepts; absent leaves the subgroup without one",
                example = "cyan")
        @Size(max = 16) String color,

        @Schema(description = "Moment a tab was closed, as the device that closed it reported "
                + "it; stored as sent and never replaced by a server clock",
                example = "2026-09-11T10:15:30Z")
        Instant closedAt,

        @Schema(description = "Name of the device a tab was closed on; absent when it has none",
                example = "Laptop")
        @Size(max = 120) String deviceName,

        @Schema(description = "Zero based position among the record's siblings", example = "0")
        Integer position) {
}
