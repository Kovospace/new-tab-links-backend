package com.kovospace.newtablinks.sync.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
 * <p>Every identifier here is chosen by the client. That is safe because it names nothing but a
 * row inside the caller's own account; see
 * {@link com.kovospace.newtablinks.common.utils.ClientAssignedIdentifierPolicy}.</p>
 *
 * @param operation        what to do with the record
 * @param entityKind       what kind of record it is
 * @param id               identifier the client uses for the record
 * @param profileId        parent profile, for an environment
 * @param environmentId    parent environment, for a group
 * @param parentGroupId    parent group, for a subgroup or a link
 * @param parentSubgroupId parent subgroup, for a link that is nested in one
 * @param name             display name, for a profile, environment, group or subgroup
 * @param description      free text, for an environment, group or subgroup
 * @param title            display text, for a link
 * @param url              address, for a link
 * @param faviconUrl       cached favicon address, for a link
 * @param collapsed        whether a subgroup is folded away right now
 * @param defaultCollapsed whether a subgroup starts folded away
 * @param catchLinksIntoTabGroup whether tabs navigating to a subgroup's links are pulled into its
 *                               browser tab group
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

        @Schema(description = "Parent profile, for an environment")
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

        @Schema(description = "Display text, for a link", example = "Spring Boot reference")
        @Size(max = 200) String title,

        @Schema(description = "Address, for a link", example = "https://spring.io")
        @Size(max = 2048) String url,

        @Schema(description = "Cached favicon address, for a link")
        @Size(max = 2048) String faviconUrl,

        @Schema(description = "Whether a subgroup is folded away right now", example = "false")
        Boolean collapsed,

        @Schema(description = "Whether a subgroup starts folded away", example = "false")
        Boolean defaultCollapsed,

        @Schema(description = "Whether tabs navigating to a subgroup's links are pulled into its "
                + "browser tab group", example = "false")
        Boolean catchLinksIntoTabGroup,

        @Schema(description = "Zero based position among the record's siblings", example = "0")
        Integer position) {
}
